package com.a501.service.domain.consulting.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.turn.TurnCredentialIssuer;
import com.a501.service.common.realtime.turn.dto.IceServersResponse;
import com.a501.service.domain.consulting.ConsultingEntryPolicy;
import com.a501.service.domain.consulting.ConsultingErrorCode;
import com.a501.service.domain.consulting.dto.response.ConsultingSessionResponse;
import com.a501.service.domain.consulting.dto.response.SessionRecordingResponse;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.entity.ParticipationRecord;
import com.a501.service.domain.consulting.entity.Reservation;
import com.a501.service.domain.consulting.realtime.ConsultingSessionEnded;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ParticipationRecordRepository;
import com.a501.service.domain.consulting.repository.ReservationRepository;
import com.a501.service.domain.contract.dto.ContractParties;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.file.service.FileLookupService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 컨설팅 세션 유스케이스 — 조회·입장·종료.
 *
 * <p>화상 연결 자체는 여기서 하지 않습니다. 들어갈 수 있는지 판정하고 기록만 남깁니다.
 *
 * <p><b>404 에 대해.</b> 세션은 존재 자체가 민감한 리소스라, 당사자가 아닌 사람에게는 403 이 아니라
 * 404 로 답해 있다는 사실도 알리지 않습니다. (BACKEND_CONVENTIONS 12.1)
 *
 * <p><b>당사자 판정.</b> 세션은 두 사람을 직접 들고 있지 않습니다. 컨설팅 합의 → 계약 → 방 →
 * 프로젝트로 내려가야 하는데, 그 길은 계약 도메인이 알고 있어 {@link ContractQueryService} 에
 * 묻습니다. 다른 도메인의 엔티티나 리포지터리를 직접 보지 않습니다. (BACKEND_CONVENTIONS 7.1)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultingSessionService {
    /** 세션 하나에 참가자는 의뢰인과 개발자 둘입니다. */
    private static final int REQUIRED_PARTICIPANTS = 2;

    /** 지금 보여 주는 안내문 판. 문구가 바뀌면 올립니다. (R-CON-17) */
    private static final short NOTICE_VERSION = 1;

    private final ConsultingSessionRepository sessionRepository;
    private final ParticipationRecordRepository participationRepository;
    private final ReservationRepository reservationRepository;
    private final ContractQueryService contractQueryService;
    private final FileLookupService fileLookupService;
    private final ApplicationEventPublisher events;
    private final TurnCredentialIssuer turnCredentialIssuer;
    private final Clock clock;

    @Autowired
    public ConsultingSessionService(
            ConsultingSessionRepository sessionRepository,
            ParticipationRecordRepository participationRepository,
            ReservationRepository reservationRepository,
            ContractQueryService contractQueryService,
            FileLookupService fileLookupService,
            ApplicationEventPublisher events,
            TurnCredentialIssuer turnCredentialIssuer) {
        this(
                sessionRepository,
                participationRepository,
                reservationRepository,
                contractQueryService,
                fileLookupService,
                events,
                turnCredentialIssuer,
                Clock.systemUTC());
    }

    /**
     * 세션 한 건을 조회합니다.
     *
     * @param userId 인증된 사용자
     * @param sessionId 세션 id
     * @return 세션 응답
     * @throws BusinessException {@link ConsultingErrorCode#SESSION_NOT_FOUND} — 없거나 지워진 세션,
     *     또는 요청자가 당사자가 아닌 경우
     */
    public ConsultingSessionResponse getSession(Integer userId, Integer sessionId) {
        ConsultingSession session = findSession(sessionId);
        ContractParties parties = findParties(session, userId);
        return ConsultingSessionResponse.of(
                session, parties.contractId(), findReservation(sessionId));
    }

    /**
     * 세션에 들어갑니다.
     *
     * <p><b>이 요청 하나가 동의와 입장을 겸합니다.</b> 양쪽이 모두 보내기 전에는 실제로
     * 들여보내지 않고, 먼저 보낸 쪽은 상대를 기다립니다. 같은 사람이 기다리는 동안 다시 보내도
     * 동의는 한 번만 기록합니다. (R-CON-17)
     *
     * <p>양쪽이 모이면 그때 들어간 시각이 참여 기록에 남고, 최초 입장이 세션 시작이 됩니다.
     * (R-CON-09)
     *
     * @param userId 인증된 사용자
     * @param sessionId 세션 id
     * @return 실제로 들어갔는지와 세션 응답
     * @throws BusinessException {@link ConsultingErrorCode#SESSION_NOT_FOUND} — 없거나 지워진 세션,
     *     또는 요청자가 당사자가 아닌 경우
     * @throws BusinessException {@link ConsultingErrorCode#SESSION_NOT_OPEN} — 아직 이른 시각이거나
     *     이미 끝난 세션인 경우
     */
    @Transactional
    public SessionEntry join(Integer userId, Integer sessionId) {
        // 같은 세션의 입장은 한 줄로 세웁니다. 겹치면 동의가 두 번 들어가거나 양쪽이 서로를
        // 못 보고 대기에 머뭅니다.
        ConsultingSession session = findSessionForEntry(sessionId);
        ContractParties parties = findParties(session, userId);
        Reservation reservation = findReservation(sessionId);

        LocalDateTime now = LocalDateTime.now(clock);
        requireOpen(session, reservation, now);

        recordConsent(sessionId, userId, now);

        List<ParticipationRecord> records =
                participationRepository.findAllByConsultingSessionIdAndDeletedFalse(sessionId);
        if (records.size() < REQUIRED_PARTICIPANTS) {
            return new SessionEntry(false, response(session, parties, reservation));
        }

        records.forEach(record -> record.enter(now));
        session.startConsulting(now);
        return new SessionEntry(true, response(session, parties, reservation));
    }

    /**
     * 세션을 마칩니다.
     *
     * <p>같은 요청을 다시 보내도 최초 종료 시각과 같은 결과가 돌아옵니다. (R-CON-15)
     *
     * @param userId 인증된 사용자
     * @param sessionId 세션 id
     * @return 종료 시각이 담긴 세션 응답
     * @throws BusinessException {@link ConsultingErrorCode#SESSION_NOT_FOUND} — 없거나 지워진 세션,
     *     또는 요청자가 당사자가 아닌 경우
     */
    @Transactional
    public ConsultingSessionResponse end(Integer userId, Integer sessionId) {
        ConsultingSession session = findSession(sessionId);
        ContractParties parties = findParties(session, userId);

        session.completeConsulting(LocalDateTime.now(clock));

        // 알림은 이 트랜잭션이 커밋된 뒤에 나갑니다. 먼저 보내면 상대가 받자마자 올리는 녹음을
        // 서버가 「아직 안 끝난 세션」으로 거절합니다.
        events.publishEvent(new ConsultingSessionEnded(sessionId, session.getEndedAt()));

        return response(session, parties, findReservation(sessionId));
    }

    /**
     * 자기 마이크로 담은 녹음을 세션에 붙입니다.
     *
     * <p><b>세션을 마친 뒤에만 받습니다.</b> 끝나기 전에는 몇 분짜리인지도 정해지지 않았습니다.
     *
     * <p>참가자당 한 건입니다 (R-CON-14). <b>같은 파일을 다시 보내면 붙어 있는 것을 그대로
     * 돌려줍니다</b> — 올리고 나서 응답을 받지 못해 다시 보내는 일이 있어, 그 되풀이를 실패로
     * 돌리면 올라간 녹음을 두고도 화면이 실패를 알립니다. 다른 파일로 바꾸려는 요청만
     * 거절합니다.
     *
     * @param userId 인증된 사용자
     * @param sessionId 세션 id
     * @param fileId 올리기를 마친 파일
     * @return 붙은 녹음
     * @throws BusinessException {@link ConsultingErrorCode#SESSION_NOT_FOUND} — 없거나 지워진
     *     세션, 또는 요청자가 당사자가 아닌 경우
     * @throws BusinessException {@link ConsultingErrorCode#SESSION_NOT_ENDED} — 아직 끝나지
     *     않은 세션인 경우
     * @throws BusinessException {@link ConsultingErrorCode#RECORDING_ALREADY_UPLOADED} — 이미
     *     다른 녹음이 붙어 있는 경우
     */
    @Transactional
    public SessionRecordingResponse attachRecording(
            Integer userId, Integer sessionId, Long fileId) {
        ConsultingSession session = findSession(sessionId);
        ContractParties parties = findParties(session, userId);
        if (!session.isEnded()) {
            throw new BusinessException(ConsultingErrorCode.SESSION_NOT_ENDED);
        }

        // 올리기를 마친 그 사람의 파일인지는 파일 도메인이 봅니다. (BACKEND_CONVENTIONS 7.1)
        fileLookupService.assertAttachable(userId, List.of(Math.toIntExact(fileId)));

        ParticipationRecord record =
                participationRepository
                        .findByConsultingSessionIdAndUserIdAndDeletedFalse(sessionId, userId)
                        .orElseThrow(
                                () -> new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND));
        Long attached = record.getFileId();
        if (attached != null && !attached.equals(fileId)) {
            throw new BusinessException(ConsultingErrorCode.RECORDING_ALREADY_UPLOADED);
        }
        record.attachRecording(fileId);

        return new SessionRecordingResponse(
                record.getId(),
                speaker(parties, userId),
                fileId,
                session.getEndedAt().atOffset(ZoneOffset.UTC));
    }

    /** 이 계약에서 이 사람이 맡은 쪽. 화자는 요청이 아니라 인증 정보로 정합니다. (R-CON-14) */
    private String speaker(ContractParties parties, Integer userId) {
        return Objects.equals(parties.clientId(), userId) ? "CLIENT" : "DEVELOPER";
    }

    /** 행이 생기는 것이 곧 동의입니다. 이미 있으면 그대로 둡니다. */
    private void recordConsent(Integer sessionId, Integer userId, LocalDateTime now) {
        participationRepository
                .findByConsultingSessionIdAndUserIdAndDeletedFalse(sessionId, userId)
                .orElseGet(
                        () ->
                                participationRepository.save(
                                        ParticipationRecord.consent(
                                                sessionId, userId, NOTICE_VERSION, now)));
    }

    private void requireOpen(
            ConsultingSession session, Reservation reservation, LocalDateTime now) {
        if (session.isEnded()
                || !ConsultingEntryPolicy.isOpen(reservation.getScheduledAt(), now)) {
            throw new BusinessException(ConsultingErrorCode.SESSION_NOT_OPEN);
        }
    }

    private ConsultingSessionResponse response(
            ConsultingSession session, ContractParties parties, Reservation reservation) {
        return ConsultingSessionResponse.of(session, parties.contractId(), reservation);
    }

    /**
     * 이 세션의 통화가 쓸 ICE 서버 목록을 돌려줍니다. (`R-CON-19`)
     *
     * <p><b>세션 당사자에게만 줍니다.</b> 중계 자격은 우리 서버로 남의 트래픽을 흘릴 수 있는
     * 열쇠라, 로그인만 했다고 주면 세션에 들어오지도 않은 사람이 중계기를 씁니다.
     *
     * <p><b>끝난 세션에도 줍니다.</b> 종료 직후 정리하는 동안 화면이 한 번 더 부를 수 있고,
     * 거절해서 얻는 것이 없습니다 — 같은 사람이 통화 중에 이미 받아 갔고 그 자격은 아직
     * 살아 있습니다 (`R-CON-19`).
     *
     * @throws BusinessException {@link ConsultingErrorCode#SESSION_NOT_FOUND} — 없거나 지워진
     *     세션, 또는 요청자가 당사자가 아닌 경우
     */
    public IceServersResponse iceServers(Integer userId, Integer sessionId) {
        findParties(findSession(sessionId), userId);
        return turnCredentialIssuer.issue(userId);
    }

    private ConsultingSession findSessionForEntry(Integer sessionId) {
        return sessionRepository
                .findWithLockByIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND));
    }

    private ConsultingSession findSession(Integer sessionId) {
        return sessionRepository
                .findByIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND));
    }

    private Reservation findReservation(Integer sessionId) {
        return reservationRepository
                .findByConsultingSessionIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND));
    }

    private ContractParties findParties(ConsultingSession session, Integer userId) {
        ContractParties parties =
                contractQueryService
                        .findPartiesByConsultingAgreementId(session.getConsultingAgreementId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ConsultingErrorCode.SESSION_NOT_FOUND));
        if (!parties.includes(userId)) {
            throw new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND);
        }
        return parties;
    }

    /**
     * 입장 결과.
     *
     * @param entered 양쪽이 모여 실제로 들어갔으면 참. 거짓이면 상대 동의를 기다리는 중
     * @param session 세션 응답
     */
    public record SessionEntry(boolean entered, ConsultingSessionResponse session) {}
}
