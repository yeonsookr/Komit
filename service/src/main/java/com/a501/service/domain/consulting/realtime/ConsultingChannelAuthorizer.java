package com.a501.service.domain.consulting.realtime;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.RealtimeChannelAuthorizer;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.domain.consulting.ConsultingErrorCode;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.entity.ParticipationRecord;
import com.a501.service.domain.consulting.entity.Reservation;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ParticipationRecordRepository;
import com.a501.service.domain.consulting.repository.ReservationRepository;
import java.time.Clock;
import java.time.Duration;
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
    /** 세션 시작 전 몇 분부터 들어갈 수 있는지. (R-CON-08) */
    private static final Duration ENTRY_OPENS_BEFORE = Duration.ofMinutes(10);

    /** 세션 하나에 참가자는 의뢰인과 개발자 둘입니다. */
    private static final int REQUIRED_PARTICIPANTS = 2;

    private final ConsultingSessionRepository sessionRepository;
    private final ParticipationRecordRepository participationRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    @Autowired
    public ConsultingChannelAuthorizer(
            ConsultingSessionRepository sessionRepository,
            ParticipationRecordRepository participationRepository,
            ReservationRepository reservationRepository) {
        this(sessionRepository, participationRepository, reservationRepository, Clock.systemUTC());
    }

    ConsultingChannelAuthorizer(
            ConsultingSessionRepository sessionRepository,
            ParticipationRecordRepository participationRepository,
            ReservationRepository reservationRepository,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.participationRepository = participationRepository;
        this.reservationRepository = reservationRepository;
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

        if (session.getEndedAt() != null) {
            throw new BusinessException(ConsultingErrorCode.SESSION_ENDED);
        }

        List<ParticipationRecord> records =
                participationRepository.findAllByConsultingSessionIdAndDeletedFalse(sessionId);

        // 당사자가 아니면 세션이 있다는 사실도 알리지 않습니다. (BACKEND_CONVENTIONS 12.1)
        boolean isParticipant =
                records.stream().anyMatch(record -> Objects.equals(record.getUserId(), userId));
        if (!isParticipant) {
            throw new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND);
        }

        // 먼저 동의한 사람은 상대를 기다립니다. 행은 동의할 때 생기므로 수가 곧 동의 수입니다. (R-CON-17)
        if (records.size() < REQUIRED_PARTICIPANTS) {
            throw new BusinessException(ConsultingErrorCode.CONSENT_REQUIRED);
        }

        requireEntryOpen(sessionId);
    }

    /** 예약 시각보다 너무 이르면 막습니다. 예약이 없으면 시각을 따질 근거가 없어 통과시킵니다. */
    private void requireEntryOpen(Integer sessionId) {
        LocalDateTime opensAt =
                reservationRepository
                        .findByConsultingSessionIdAndDeletedFalse(sessionId)
                        .map(Reservation::getScheduledAt)
                        .map(scheduledAt -> scheduledAt.minus(ENTRY_OPENS_BEFORE))
                        .orElse(null);

        if (opensAt != null && LocalDateTime.now(clock).isBefore(opensAt)) {
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
