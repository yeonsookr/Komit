package com.a501.service.domain.consulting.realtime;

import com.a501.service.domain.contract.service.ContractQueryService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.domain.consulting.ConsultingErrorCode;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.entity.ParticipationRecord;
import com.a501.service.domain.consulting.entity.Reservation;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ParticipationRecordRepository;
import com.a501.service.domain.consulting.repository.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class ConsultingChannelAuthorizerTest {
    private static final Integer SESSION_ID = 42;
    private static final Integer CLIENT_ID = 7;
    private static final Integer DEVELOPER_ID = 9;
    private static final Integer OUTSIDER_ID = 99;

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final LocalDateTime SCHEDULED_AT =
            LocalDateTime.ofInstant(Instant.parse("2026-09-21T10:05:00Z"), ZoneOffset.UTC);

    private final ConsultingSessionRepository sessionRepository =
            mock(ConsultingSessionRepository.class);
    private final ParticipationRecordRepository participationRepository =
            mock(ParticipationRecordRepository.class);
    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final ContractQueryService contractQueryService = mock(ContractQueryService.class);

    private final ConsultingChannelAuthorizer authorizer =
            new ConsultingChannelAuthorizer(
                    sessionRepository,
                    participationRepository,
                    reservationRepository,
                    contractQueryService,
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 맡은_채널은_컨설팅_세션이다() {
        assertThat(authorizer.channel()).isEqualTo(RealtimeChannelType.CONSULTING_SESSION);
    }

    @Test
    void 양쪽이_동의하고_입장_시각이_열렸으면_통과한다() {
        givenSession(null);
        givenParticipants(CLIENT_ID, DEVELOPER_ID);
        givenReservation(SCHEDULED_AT);

        assertThatCode(() -> authorizer.authorize(CLIENT_ID, "42")).doesNotThrowAnyException();
    }

    /** R-CON-21: 취소된 계약의 세션에는 통화 채널을 열지 않는다. 입장 API 와 같은 판정이다. */
    @Test
    void 취소된_계약의_세션은_막는다() {
        givenSession(null);
        givenParticipants(CLIENT_ID, DEVELOPER_ID);
        givenReservation(SCHEDULED_AT);
        when(contractQueryService.isCancelledByConsultingAgreementId(org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        assertThatThrownBy(() -> authorizer.authorize(CLIENT_ID, "42"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_CANCELLED);
    }

    @Test
    void 없는_세션은_막는다() {
        when(sessionRepository.findByIdAndDeletedFalse(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizer.authorize(CLIENT_ID, "42"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void 세션_번호가_숫자가_아니면_막는다() {
        assertThatThrownBy(() -> authorizer.authorize(CLIENT_ID, "마흔둘"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_FOUND);
    }

    /** 당사자가 아닌 사람에게는 세션이 있다는 사실도 알리지 않는다. */
    @Test
    void 당사자가_아니면_없는_세션과_같게_막는다() {
        givenSession(null);
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        assertThatThrownBy(() -> authorizer.authorize(OUTSIDER_ID, "42"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void 당사자가_아니면_끝난_세션이라는_사실도_알리지_않는다() {
        givenSession(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        assertThatThrownBy(() -> authorizer.authorize(OUTSIDER_ID, "42"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_FOUND);
        verify(reservationRepository, never())
                .findByConsultingSessionIdAndDeletedFalse(SESSION_ID);
    }

    @Test
    void 끝난_세션은_막는다() {
        givenSession(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        assertThatThrownBy(() -> authorizer.authorize(CLIENT_ID, "42"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_ENDED);
    }

    /**
     * 먼저 동의한 사람도 채널에는 들어온다. 그래야 상대가 들어온 것을 서버가 알려 줄 수 있고,
     * 기다리는 쪽이 같은 입장 요청을 되풀이하지 않아도 된다. 실제 입장과 세션 시작은 입장
     * API 가 막는다. (R-CON-17)
     */
    @Test
    void 한쪽만_동의했어도_채널에는_들어온다() {
        givenSession(null);
        givenParticipants(CLIENT_ID);
        givenReservation(SCHEDULED_AT);

        assertThatCode(() -> authorizer.authorize(CLIENT_ID, "42")).doesNotThrowAnyException();
    }

    /** 시작 10분 전부터 들어갈 수 있다. (R-CON-08) */
    @Test
    void 입장_시각보다_이르면_막는다() {
        givenSession(null);
        givenParticipants(CLIENT_ID, DEVELOPER_ID);
        givenReservation(SCHEDULED_AT.plusMinutes(30));

        assertThatThrownBy(() -> authorizer.authorize(CLIENT_ID, "42"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.ENTRY_NOT_OPEN);
    }

    /** 예약이 없으면 입장 가능 시각을 검증할 수 없으므로 안전하게 막는다. */
    @Test
    void 예약이_없으면_입장을_막는다() {
        givenSession(null);
        givenParticipants(CLIENT_ID, DEVELOPER_ID);
        when(reservationRepository.findByConsultingSessionIdAndDeletedFalse(SESSION_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizer.authorize(CLIENT_ID, "42"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.ENTRY_NOT_OPEN);
    }

    private void givenSession(LocalDateTime endedAt) {
        ConsultingSession session = BeanUtils.instantiateClass(ConsultingSession.class);
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        ReflectionTestUtils.setField(session, "endedAt", endedAt);
        when(sessionRepository.findByIdAndDeletedFalse(SESSION_ID)).thenReturn(Optional.of(session));
    }

    private void givenParticipants(Integer... userIds) {
        List<ParticipationRecord> records =
                Arrays.stream(userIds).map(this::participation).toList();
        when(participationRepository.findAllByConsultingSessionIdAndDeletedFalse(SESSION_ID))
                .thenReturn(records);
    }

    private ParticipationRecord participation(Integer userId) {
        ParticipationRecord record = BeanUtils.instantiateClass(ParticipationRecord.class);
        ReflectionTestUtils.setField(record, "consultingSessionId", SESSION_ID);
        ReflectionTestUtils.setField(record, "userId", userId);
        ReflectionTestUtils.setField(record, "consentedAt", LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        return record;
    }

    private void givenReservation(LocalDateTime scheduledAt) {
        Reservation reservation = BeanUtils.instantiateClass(Reservation.class);
        ReflectionTestUtils.setField(reservation, "consultingSessionId", SESSION_ID);
        ReflectionTestUtils.setField(reservation, "scheduledAt", scheduledAt);
        when(reservationRepository.findByConsultingSessionIdAndDeletedFalse(SESSION_ID))
                .thenReturn(Optional.of(reservation));
    }
}
