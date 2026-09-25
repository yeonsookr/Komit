package com.a501.service.domain.consulting.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.consulting.ConsultingErrorCode;
import com.a501.service.domain.consulting.dto.Speaker;
import com.a501.service.domain.consulting.dto.request.TranscriptFailureRequest;
import com.a501.service.domain.consulting.dto.request.TranscriptResultRequest;
import com.a501.service.domain.consulting.dto.response.TranscriptionJobResponse;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.entity.ParticipationRecord;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ParticipationRecordRepository;
import com.a501.service.domain.contract.dto.ContractParties;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.file.service.FileLookupService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * GPU 노트북이 전사를 받아 가고 결과를 올리는 유스케이스. (`ai/worker/docs/job-contract.md` 11절)
 *
 * <p><b>상태는 세션의 두 컬럼으로 읽습니다.</b> 전사가 비어 있고 전사 불가 판정도 없으면 받아 갈
 * 대상입니다. 노트북은 한 대라 「가져감」 표시를 두지 않습니다 — 노트북이 전사 도중 꺼지면 전사가
 * 비어 있으니 다시 켰을 때 같은 세션을 받아 갑니다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class TranscriptionJobService {
    private final ConsultingSessionRepository sessionRepository;
    private final ParticipationRecordRepository participationRepository;
    private final ContractQueryService contractQueryService;
    private final FileLookupService fileLookupService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public TranscriptionJobService(
            ConsultingSessionRepository sessionRepository,
            ParticipationRecordRepository participationRepository,
            ContractQueryService contractQueryService,
            FileLookupService fileLookupService,
            ObjectMapper objectMapper) {
        this(
                sessionRepository,
                participationRepository,
                contractQueryService,
                fileLookupService,
                objectMapper,
                Clock.systemUTC());
    }

    TranscriptionJobService(
            ConsultingSessionRepository sessionRepository,
            ParticipationRecordRepository participationRepository,
            ContractQueryService contractQueryService,
            FileLookupService fileLookupService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.participationRepository = participationRepository;
        this.contractQueryService = contractQueryService;
        this.fileLookupService = fileLookupService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 다음에 전사할 세션 하나를 녹음 읽기 주소와 함께 내줍니다.
     *
     * <p>녹음은 이 서버를 지나지 않습니다. 노트북이 읽기 주소로 저장소에서 직접 받습니다.
     *
     * @return 할 일. 없으면 비어 있음
     */
    public Optional<TranscriptionJobResponse> claim() {
        return sessionRepository.findNextToTranscribe().map(this::job);
    }

    /**
     * 전사 결과를 세션에 남깁니다.
     *
     * <p>이미 전사가 있거나 전사 불가로 판정된 세션이면 바꾸지 않고 성공으로 답합니다. 노트북은
     * 거절받아도 할 수 있는 일이 없습니다. 두 인스턴스가 같은 세션을 동시에 받아도 행을 잠가
     * 한 줄로 세웁니다.
     *
     * @throws BusinessException {@link ConsultingErrorCode#SESSION_NOT_FOUND} — 없는 세션
     */
    @Transactional
    public void recordResult(Integer sessionId, TranscriptResultRequest request) {
        findForUpdate(sessionId).recordTranscript(objectMapper.writeValueAsString(request));
    }

    /**
     * 전사 실패를 받습니다.
     *
     * <p>녹음을 쓸 수 없으면 전사 불가로 판정해 다시 내주지 않습니다. 노트북 쪽 문제면 아무것도
     * 바꾸지 않아 다음에 같은 세션이 다시 나갑니다. 어느 쪽이든 사람이 알아채야 하므로 남깁니다.
     *
     * @throws BusinessException {@link ConsultingErrorCode#SESSION_NOT_FOUND} — 없는 세션
     */
    @Transactional
    public void recordFailure(Integer sessionId, TranscriptFailureRequest request) {
        ConsultingSession session = findForUpdate(sessionId);
        log.warn("전사 실패 보고: sessionId={}, code={}, message={}",
                sessionId, request.code(), request.message());
        if (request.code() == TranscriptFailureRequest.Code.AUDIO_UNUSABLE) {
            session.markTranscriptFailed(LocalDateTime.now(clock));
        }
    }

    private TranscriptionJobResponse job(Integer sessionId) {
        ConsultingSession session = sessionRepository.findByIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> new IllegalStateException("전사할 세션이 사라졌습니다: " + sessionId));
        ContractParties parties = contractQueryService
                .findPartiesByConsultingAgreementId(session.getConsultingAgreementId())
                .orElseThrow(() -> new IllegalStateException("세션의 계약을 찾지 못했습니다: " + sessionId));
        List<ParticipationRecord> recorded =
                participationRepository.findAllByConsultingSessionIdAndDeletedFalse(sessionId).stream()
                        .filter(record -> record.getFileId() != null)
                        .toList();
        Map<Integer, String> urls = fileLookupService.findReadUrls(
                recorded.stream().map(record -> Math.toIntExact(record.getFileId())).toList());

        List<TranscriptionJobResponse.Track> tracks = recorded.stream()
                .map(record -> new TranscriptionJobResponse.Track(
                        speaker(parties, record.getUserId()),
                        urls.get(Math.toIntExact(record.getFileId()))))
                .toList();
        // 붙어 있던 녹음 파일이 저장소에서 사라진 경우다. 한 사람 몫만 넘기면 그 사람의 말만으로
        // 요약이 만들어지므로 넘기지 않고 드러낸다.
        if (tracks.stream().anyMatch(track -> track.audioUrl() == null)) {
            throw new IllegalStateException("녹음 파일의 읽기 주소를 만들지 못했습니다: " + sessionId);
        }
        return new TranscriptionJobResponse(sessionId, tracks);
    }

    /** 화자는 녹음을 올린 사람의 계약상 역할입니다. (R-CON-14) */
    private static Speaker speaker(ContractParties parties, Integer userId) {
        return Objects.equals(parties.clientId(), userId) ? Speaker.CLIENT : Speaker.DEVELOPER;
    }

    private ConsultingSession findForUpdate(Integer sessionId) {
        return sessionRepository.findWithLockByIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND));
    }
}
