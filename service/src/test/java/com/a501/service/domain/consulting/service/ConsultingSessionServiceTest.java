package com.a501.service.domain.consulting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.consulting.ConsultingErrorCode;
import com.a501.service.domain.consulting.dto.response.SessionParticipationResponse;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.entity.ParticipationRecord;
import com.a501.service.domain.consulting.entity.Reservation;
import com.a501.service.domain.consulting.realtime.ConsultingSessionEnded;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ParticipationRecordRepository;
import com.a501.service.domain.consulting.repository.ReservationRepository;
import com.a501.service.domain.contract.dto.ContractParties;
import com.a501.service.common.realtime.turn.TurnCredentialIssuer;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.file.service.FileLookupService;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class ConsultingSessionServiceTest {
    private static final Integer SESSION_ID = 42;
    private static final Integer AGREEMENT_ID = 11;
    private static final Integer CONTRACT_ID = 120;
    private static final Integer CLIENT_ID = 7;
    private static final Integer DEVELOPER_ID = 9;
    private static final Integer OUTSIDER_ID = 99;

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final LocalDateTime NOW_AT = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    /** 시작 5분 전이라 10분 창 안이다. (R-CON-08) */
    private static final LocalDateTime SCHEDULED_AT = NOW_AT.plusMinutes(5);

    private final ConsultingSessionRepository sessionRepository =
            mock(ConsultingSessionRepository.class);
    private final ParticipationRecordRepository participationRepository =
            mock(ParticipationRecordRepository.class);
    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final ContractQueryService contractQueryService = mock(ContractQueryService.class);
    private final FileLookupService fileLookupService = mock(FileLookupService.class);
    private final TurnCredentialIssuer turnCredentialIssuer = mock(TurnCredentialIssuer.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final ConsultingSessionService service =
            new ConsultingSessionService(
                    sessionRepository,
                    participationRepository,
                    reservationRepository,
                    contractQueryService,
                    fileLookupService,
                    events,
                    turnCredentialIssuer,
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        givenSession(null);
        givenReservation(SCHEDULED_AT);
        when(contractQueryService.findPartiesByConsultingAgreementId(AGREEMENT_ID))
                .thenReturn(Optional.of(new ContractParties(CONTRACT_ID, CLIENT_ID, DEVELOPER_ID)));
        when(participationRepository.save(any()))
                .thenAnswer(call -> call.getArgument(0, ParticipationRecord.class));
    }

    @Test
    void 세션을_조회하면_돌아갈_계약이_함께_온다() {
        givenParticipants();

        var response = service.getSession(CLIENT_ID, SESSION_ID);

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        // 세션이 달린 컨설팅 합의가 아니라 그 위의 계약이어야 방으로 돌아갈 수 있다.
        assertThat(response.contractId()).isEqualTo(CONTRACT_ID);
        assertThat(response.price()).isEqualTo(50000L);
    }

    /** 계약 경계에서는 offset 이 있어야 한다. (BACKEND_CONVENTIONS 11.1) */
    @Test
    void 시각은_offset_을_달고_나간다() {
        givenParticipants();

        var response = service.getSession(CLIENT_ID, SESSION_ID);

        assertThat(response.reservation().scheduledAt())
                .isEqualTo(SCHEDULED_AT.atOffset(ZoneOffset.UTC));
        assertThat(response.reservation().scheduledAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void 당사자가_아니면_세션이_있다는_것도_알리지_않는다() {
        givenParticipants();

        assertThatThrownBy(() -> service.getSession(OUTSIDER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void 계약을_거슬러_올라가지_못하면_없는_세션으로_다룬다() {
        when(contractQueryService.findPartiesByConsultingAgreementId(AGREEMENT_ID))
                .thenReturn(Optional.empty());
        givenParticipants();

        assertThatThrownBy(() -> service.getSession(CLIENT_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_FOUND);
    }

    /*
       화면이 「내가 이미 동의했나」를 가리는 근거다. 행이 있다는 것이 곧 동의다 (R-CON-17).
       이것이 없으면 통화 중에 새로고침한 사람에게 안내부터 다시 묻게 된다.
    */
    @Test
    void 동의한_사람만_참가자_기록에_있다() {
        givenParticipants(CLIENT_ID);

        var records = service.participations(CLIENT_ID, SESSION_ID);

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().userId()).isEqualTo(CLIENT_ID);
        assertThat(records.getFirst().consentedAt()).isEqualTo(NOW_AT.atOffset(ZoneOffset.UTC));
        // 아직 상대를 기다리는 중이라 들어간 시각은 없다.
        assertThat(records.getFirst().joinedAt()).isNull();
    }

    @Test
    void 아무도_동의하지_않았으면_빈_목록이다() {
        givenParticipants();

        assertThat(service.participations(CLIENT_ID, SESSION_ID)).isEmpty();
    }

    /** 상대가 이미 동의했는지도 안내에 적어야 해서 함께 준다. (R-CON-13) */
    @Test
    void 상대의_기록도_함께_준다() {
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        assertThat(service.participations(CLIENT_ID, SESSION_ID))
                .extracting(SessionParticipationResponse::userId)
                .containsExactly(CLIENT_ID, DEVELOPER_ID);
    }

    @Test
    void 당사자가_아니면_참가자_기록도_볼_수_없다() {
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        assertThatThrownBy(() -> service.participations(OUTSIDER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_FOUND);
    }

    /** R-CON-21: 취소된 계약의 세션은 열지 않고 동의도 남기지 않는다. */
    @Test
    void 취소된_계약의_세션에는_들어가지_못한다() {
        givenParticipants();
        when(contractQueryService.isCancelledByConsultingAgreementId(AGREEMENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.join(CLIENT_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_CANCELLED);
        verify(participationRepository, never()).save(any());
    }

    @Test
    void 먼저_동의한_사람은_상대를_기다린다() {
        givenParticipants(CLIENT_ID);

        var entry = service.join(CLIENT_ID, SESSION_ID);

        assertThat(entry.entered()).isFalse();
        assertThat(entry.session().startedAt()).isNull();
    }

    @Test
    void 양쪽이_모이면_들어가고_시작_시각이_찍힌다() {
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        var entry = service.join(DEVELOPER_ID, SESSION_ID);

        assertThat(entry.entered()).isTrue();
        assertThat(entry.session().startedAt()).isEqualTo(NOW_AT.atOffset(ZoneOffset.UTC));
    }

    /** 기다리는 동안 화면이 같은 요청을 되풀이한다. 동의가 두 번 기록되면 안 된다. (R-CON-17) */
    @Test
    void 같은_사람이_다시_보내도_동의는_한_번만_기록한다() {
        givenParticipants(CLIENT_ID);

        service.join(CLIENT_ID, SESSION_ID);

        verify(participationRepository, never()).save(any());
    }

    @Test
    void 처음_동의하는_사람은_기록을_남긴다() {
        givenParticipants();

        service.join(CLIENT_ID, SESSION_ID);

        verify(participationRepository).save(any(ParticipationRecord.class));
    }

    @Test
    void 계약이_성립하기_전이면_막고_동의를_남기지_않는다() {
        givenReservation(SCHEDULED_AT, "PROPOSED");
        givenParticipants();

        assertThatThrownBy(() -> service.join(CLIENT_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_OPEN);
        verify(participationRepository, never()).save(any());
    }

    @Test
    void 아직_이른_시각이면_막는다() {
        givenReservation(NOW_AT.plusMinutes(11));
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        assertThatThrownBy(() -> service.join(CLIENT_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_OPEN);
    }

    @Test
    void 이미_끝난_세션도_같은_코드로_막는다() {
        givenSession(NOW_AT.minusMinutes(1));
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        assertThatThrownBy(() -> service.join(CLIENT_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_OPEN);
    }

    @Test
    void 종료는_한_번만_확정된다() {
        LocalDateTime firstEnd = NOW_AT.minusMinutes(3);
        givenSession(firstEnd);
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        var response = service.end(DEVELOPER_ID, SESSION_ID);

        assertThat(response.endedAt()).isEqualTo(firstEnd.atOffset(ZoneOffset.UTC));
    }

    @Test
    void 끝나지_않은_세션은_지금_시각으로_마친다() {
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        var response = service.end(CLIENT_ID, SESSION_ID);

        assertThat(response.endedAt()).isEqualTo(NOW_AT.atOffset(ZoneOffset.UTC));
    }

    /**
     * 커밋 뒤에 나가야 한다. 먼저 보내면 상대가 받자마자 올리는 녹음을 서버가
     * 「아직 안 끝난 세션」으로 거절한다.
     */
    @Test
    void 종료를_마치면_세션_채널에_알린다() {
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        service.end(CLIENT_ID, SESSION_ID);

        verify(events).publishEvent(new ConsultingSessionEnded(SESSION_ID, NOW_AT));
    }

    /** 종료가 한 번만 확정되므로 알림도 최초 시각 그대로다. (R-CON-15) */
    @Test
    void 다시_종료해도_알림은_최초_시각을_싣는다() {
        LocalDateTime firstEnd = NOW_AT.minusMinutes(3);
        givenSession(firstEnd);
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        service.end(DEVELOPER_ID, SESSION_ID);

        verify(events).publishEvent(new ConsultingSessionEnded(SESSION_ID, firstEnd));
    }

    @Test
    void 녹음은_세션을_마친_뒤에만_받는다() {
        givenSession(null);
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        assertThatThrownBy(() -> service.attachRecording(CLIENT_ID, SESSION_ID, 612L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_ENDED);
    }

    @Test
    void 화자는_요청이_아니라_인증_정보로_정한다() {
        givenSession(NOW_AT);
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        assertThat(service.attachRecording(CLIENT_ID, SESSION_ID, 612L).speaker()).isEqualTo("CLIENT");
        assertThat(service.attachRecording(DEVELOPER_ID, SESSION_ID, 613L).speaker())
                .isEqualTo("DEVELOPER");
    }

    /** 올리고 나서 응답을 받지 못해 다시 보내는 일이 있다. 그 되풀이를 실패로 돌리지 않는다. */
    @Test
    void 같은_파일을_다시_보내면_붙어_있는_것을_그대로_돌려준다() {
        givenSession(NOW_AT);
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        var first = service.attachRecording(CLIENT_ID, SESSION_ID, 612L);
        var again = service.attachRecording(CLIENT_ID, SESSION_ID, 612L);

        assertThat(again.fileId()).isEqualTo(first.fileId());
        assertThat(again.recordingId()).isEqualTo(first.recordingId());
    }

    @Test
    void 다른_파일로_바꾸려는_요청은_거절한다() {
        givenSession(NOW_AT);
        givenParticipants(CLIENT_ID, DEVELOPER_ID);
        service.attachRecording(CLIENT_ID, SESSION_ID, 612L);

        assertThatThrownBy(() -> service.attachRecording(CLIENT_ID, SESSION_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ConsultingErrorCode.RECORDING_ALREADY_UPLOADED);
    }

    @Test
    void 당사자가_아니면_녹음도_붙일_수_없다() {
        givenSession(NOW_AT);
        givenParticipants(CLIENT_ID, DEVELOPER_ID);

        assertThatThrownBy(() -> service.attachRecording(OUTSIDER_ID, SESSION_ID, 612L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_FOUND);
    }

    private void givenSession(LocalDateTime endedAt) {
        ConsultingSession session = BeanUtils.instantiateClass(ConsultingSession.class);
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        ReflectionTestUtils.setField(session, "consultingAgreementId", AGREEMENT_ID);
        ReflectionTestUtils.setField(session, "price", 50000);
        ReflectionTestUtils.setField(session, "durationMinutes", 30);
        ReflectionTestUtils.setField(session, "chatRefId", "session-42-chat");
        ReflectionTestUtils.setField(session, "endedAt", endedAt);
        when(sessionRepository.findByIdAndDeletedFalse(SESSION_ID)).thenReturn(Optional.of(session));
        // 입장은 세션 행을 잠그고 가져옵니다. 같은 세션을 돌려주게 둡니다.
        when(sessionRepository.findWithLockByIdAndDeletedFalse(SESSION_ID))
                .thenReturn(Optional.of(session));
    }

    private void givenParticipants(Integer... userIds) {
        List<ParticipationRecord> records =
                new ArrayList<>(Arrays.stream(userIds).map(this::participation).toList());
        when(participationRepository.findAllByConsultingSessionIdAndDeletedFalse(SESSION_ID))
                .thenReturn(records);
        for (ParticipationRecord record : records) {
            when(participationRepository.findByConsultingSessionIdAndUserIdAndDeletedFalse(
                            SESSION_ID, record.getUserId()))
                    .thenReturn(Optional.of(record));
        }
    }

    private ParticipationRecord participation(Integer userId) {
        return ParticipationRecord.consent(SESSION_ID, userId, (short) 1, NOW_AT);
    }

    private void givenReservation(LocalDateTime scheduledAt) {
        givenReservation(scheduledAt, "CONFIRMED");
    }

    private void givenReservation(LocalDateTime scheduledAt, String status) {
        Reservation reservation = BeanUtils.instantiateClass(Reservation.class);
        ReflectionTestUtils.setField(reservation, "id", 71);
        ReflectionTestUtils.setField(reservation, "consultingSessionId", SESSION_ID);
        ReflectionTestUtils.setField(reservation, "scheduledAt", scheduledAt);
        ReflectionTestUtils.setField(reservation, "status", status);
        when(reservationRepository.findByConsultingSessionIdAndDeletedFalse(SESSION_ID))
                .thenReturn(Optional.of(reservation));
    }
}
