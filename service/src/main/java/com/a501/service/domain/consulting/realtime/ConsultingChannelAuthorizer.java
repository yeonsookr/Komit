package com.a501.service.domain.consulting.realtime;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.RealtimeChannelAuthorizer;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.domain.consulting.ConsultingEntryPolicy;
import com.a501.service.domain.consulting.ConsultingErrorCode;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.entity.ParticipationRecord;
import com.a501.service.domain.consulting.entity.Reservation;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ParticipationRecordRepository;
import com.a501.service.domain.consulting.repository.ReservationRepository;
import com.a501.service.domain.contract.service.ContractQueryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 입장권을 내줄지 판정합니다.
 *
 * <p>티켓 하나로 연결이 열리므로 판정은 여기서 끝나야 합니다. 소켓이 붙은 뒤에는 다시 묻지
 * 않습니다.
 */
@Component
public class ConsultingChannelAuthorizer implements RealtimeChannelAuthorizer {
    private final ConsultingSessionRepository sessionRepository;
    private final ParticipationRecordRepository participationRepository;
    private final ReservationRepository reservationRepository;
    private final ContractQueryService contractQueryService;
    private final Clock clock;

    @Autowired
    public ConsultingChannelAuthorizer(
            ConsultingSessionRepository sessionRepository,
            ParticipationRecordRepository participationRepository,
            ReservationRepository reservationRepository,
            ContractQueryService contractQueryService) {
        this(sessionRepository, participationRepository, reservationRepository, contractQueryService,
                Clock.systemUTC());
    }

    ConsultingChannelAuthorizer(
            ConsultingSessionRepository sessionRepository,
            ParticipationRecordRepository participationRepository,
            ReservationRepository reservationRepository,
            ContractQueryService contractQueryService,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.participationRepository = participationRepository;
        this.reservationRepository = reservationRepository;
        this.contractQueryService = contractQueryService;
        this.clock = clock;
    }

    @Override
    public RealtimeChannelType channel() {
        return RealtimeChannelType.CONSULTING_SESSION;
    }

    @Override
    @Transactional(readOnly = true)
    public void authorize(Integer userId, String channelId) {
        Integer sessionId = parseSessionId(channelId);

        ConsultingSession session =
                sessionRepository
                        .findByIdAndDeletedFalse(sessionId)
                        .orElseThrow(
                                () -> new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND));

        List<ParticipationRecord> records =
                participationRepository.findAllByConsultingSessionIdAndDeletedFalse(sessionId);

        // 당사자가 아니면 세션이 있다는 사실도 알리지 않습니다. (BACKEND_CONVENTIONS 12.1)
        boolean isParticipant =
                records.stream().anyMatch(record -> Objects.equals(record.getUserId(), userId));
        if (!isParticipant) {
            throw new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND);
        }

        // R-CON-21: 취소된 계약의 세션에는 통화 채널을 열지 않는다. 입장 API 와 같은 판정이다.
        if (contractQueryService.isCancelledByConsultingAgreementId(session.getConsultingAgreementId())) {
            throw new BusinessException(ConsultingErrorCode.SESSION_CANCELLED);
        }

        if (session.getEndedAt() != null) {
            throw new BusinessException(ConsultingErrorCode.SESSION_ENDED);
        }

        // 동의한 사람은 상대를 기다리는 동안에도 채널에 들어옵니다. 그래야 상대가 들어온 것을
        // 서버가 알려 줄 수 있습니다. 실제 입장과 세션 시작은 입장 API 가 막습니다. (R-CON-17)

        requireEntryOpen(sessionId);
    }

    /** 예약 시각보다 너무 이르거나 예약을 확인할 수 없으면 막습니다. */
    private void requireEntryOpen(Integer sessionId) {
        LocalDateTime scheduledAt =
                reservationRepository
                        .findByConsultingSessionIdAndDeletedFalse(sessionId)
                        .map(Reservation::getScheduledAt)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ConsultingErrorCode.ENTRY_NOT_OPEN));

        // 입장 API 와 같은 값을 봐야 합니다. 갈리면 화면은 들여보내는데 소켓이 막힙니다.
        if (!ConsultingEntryPolicy.isOpen(scheduledAt, LocalDateTime.now(clock))) {
            throw new BusinessException(ConsultingErrorCode.ENTRY_NOT_OPEN);
        }
    }

    private Integer parseSessionId(String channelId) {
        try {
            return Integer.valueOf(channelId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND);
        }
    }
}
