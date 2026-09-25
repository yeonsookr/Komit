package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.entity.Reservation;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ReservationRepository;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.request.AmendConsultingContractRequest;
import com.a501.service.domain.contract.dto.request.WriteConsultingContractRequest;
import com.a501.service.domain.contract.entity.AiContractDraftSnapshot;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.TempMessage;
import com.a501.service.domain.contract.repository.AiContractDraftSnapshotRepository;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.TempMessageRepository;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 컨설팅 계약서를 쓰고 고치는 규칙을 DB 없이 검증합니다.
 *
 * <p>계약서는 개발자가 쓰고, 수정 제안은 방의 두 사람이 모두 보냅니다. 서명한 뒤에는 고칠 수 없습니다.
 * (`R-CON-02`)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsultingContractWriteServiceTest {
    private static final Integer ROOM_ID = 92;
    private static final Integer CONTRACT_ID = 120;
    private static final Integer CLIENT_ID = 9001;
    private static final Integer DEVELOPER_USER_ID = 9101;
    private static final Integer STRANGER_ID = 9999;
    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");
    private static final OffsetDateTime FUTURE =
            OffsetDateTime.parse("2026-12-01T14:00:00+09:00");

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ConsultingAgreementRepository consultingAgreementRepository;
    @Mock
    private AiContractDraftSnapshotRepository snapshotRepository;
    @Mock
    private ConsultingSessionRepository consultingSessionRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private TempMessageRepository tempMessageRepository;
    @Mock
    private RoomQueryService roomQueryService;
    @Mock
    private ContractDetailService contractDetailService;

    private ConsultingContractWriteService service;
    private Contract contract;
    private ConsultingAgreement agreement;
    private ConsultingSession session;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        service = new ConsultingContractWriteService(
                contractRepository,
                consultingAgreementRepository,
                snapshotRepository,
                consultingSessionRepository,
                reservationRepository,
                tempMessageRepository,
                roomQueryService,
                contractDetailService,
                Clock.fixed(NOW, ZoneOffset.UTC));

        contract = Contract.of(ROOM_ID, Contract.ContractType.CONSULTING);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        agreement = ConsultingAgreement.of(
                CONTRACT_ID, "회원탈퇴 경로를 봅니다", 50_000, "관리자 화면 접근 경로를 확인합니다");
        ReflectionTestUtils.setField(agreement, "id", 7);
        session = ConsultingSession.of(7, 50_000, 30);
        ReflectionTestUtils.setField(session, "id", 31);
        reservation = Reservation.of(31, FUTURE.toLocalDateTime());

        when(roomQueryService.findParties(ROOM_ID))
                .thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_USER_ID)));
        when(contractRepository.save(any(Contract.class))).thenReturn(contract);
        when(consultingAgreementRepository.save(any(ConsultingAgreement.class)))
                .thenReturn(agreement);
        when(consultingSessionRepository.save(any(ConsultingSession.class))).thenReturn(session);
        when(contractRepository.findByIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(contract));
        when(consultingAgreementRepository.findByContractIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(agreement));
        when(consultingSessionRepository
                        .findAllByConsultingAgreementIdAndDeletedFalseOrderByIdAsc(7))
                .thenReturn(List.of(session));
        when(reservationRepository.findByConsultingSessionIdAndDeletedFalse(31))
                .thenReturn(Optional.of(reservation));
    }

    @Test
    @DisplayName("개발자가 쓰면 계약·합의·세션·예약이 함께 생긴다")
    void writesFourRows() {
        service.write(
                ROOM_ID,
                DEVELOPER_USER_ID,
                new WriteConsultingContractRequest(
                        "회원탈퇴 경로를 봅니다",
                        FUTURE,
                        30,
                        50_000,
                        "탈퇴 데이터 보관 방법을 확인합니다",
                        null));

        verify(contractRepository).save(any(Contract.class));
        ArgumentCaptor<ConsultingAgreement> agreementCaptor =
                ArgumentCaptor.forClass(ConsultingAgreement.class);
        verify(consultingAgreementRepository).save(agreementCaptor.capture());
        assertThat(agreementCaptor.getValue().getDetail()).isEqualTo("탈퇴 데이터 보관 방법을 확인합니다");
        verify(consultingSessionRepository).save(any(ConsultingSession.class));
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    @DisplayName("AI 초안이 있으면 같은 계약과 합의를 고치고 세션과 예약만 만든다")
    void completesSavedDraft() {
        when(contractRepository.findByRoomIdAndDeletedFalse(ROOM_ID)).thenReturn(Optional.of(contract));
        when(snapshotRepository.findByContractId(CONTRACT_ID))
                .thenReturn(Optional.of(AiContractDraftSnapshot.of(CONTRACT_ID, "{\"draft\":{}}")));

        service.write(
                ROOM_ID,
                DEVELOPER_USER_ID,
                new WriteConsultingContractRequest(
                        "확정 주제", FUTURE, 40, 80_000, "확정 상세", null));

        verify(contractRepository, never()).save(any());
        verify(consultingAgreementRepository, never()).save(any());
        assertThat(agreement.getTopic()).isEqualTo("확정 주제");
        assertThat(agreement.getTotalAmount()).isEqualTo(80_000);
        assertThat(agreement.getDetail()).isEqualTo("확정 상세");
        verify(consultingSessionRepository).save(any(ConsultingSession.class));
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    @DisplayName("협의중으로 시작하고 아직 아무도 서명하지 않았다")
    void startsNegotiating() {
        service.write(ROOM_ID, DEVELOPER_USER_ID, request(FUTURE, 50_000));

        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("NEGOTIATING");
        assertThat(captor.getValue().isSigned()).isFalse();
    }

    @Test
    @DisplayName("세션에 채팅 참조 키를 붙인다")
    void assignsSessionChatRef() {
        service.write(ROOM_ID, DEVELOPER_USER_ID, request(FUTURE, 50_000));

        // 방 대화와 한 이름 공간을 쓰므로 앞자리로 가른다. (`R-MATCH-06`)
        assertThat(session.getChatRefId()).isEqualTo("session-31-chat");
    }

    @Test
    @DisplayName("계약서는 개발자가 쓴다")
    void onlyDeveloperWrites() {
        assertThatThrownBy(() -> service.write(ROOM_ID, CLIENT_ID, request(FUTURE, 50_000)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.FORBIDDEN);
        verify(contractRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 방이면 404 다")
    void rejectsMissingRoom() {
        when(roomQueryService.findParties(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.write(404, DEVELOPER_USER_ID, request(FUTURE, 50_000)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("지난 시각으로는 약속을 잡을 수 없다")
    void rejectsPastSchedule() {
        OffsetDateTime past = OffsetDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC);

        assertThatThrownBy(() -> service.write(ROOM_ID, DEVELOPER_USER_ID, request(past, 50_000)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_REQUEST);
        verify(contractRepository, never()).save(any());
    }

    @Test
    @DisplayName("전달 메시지를 적지 않으면 남기지 않는다")
    void skipsBlankMessage() {
        service.write(
                ROOM_ID,
                DEVELOPER_USER_ID,
                new WriteConsultingContractRequest("주제", FUTURE, 30, 50_000, null, "   "));

        verify(tempMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("보낸 항목만 바뀐다")
    void amendsOnlyGivenFields() {
        service.amend(
                CONTRACT_ID,
                CLIENT_ID,
                new AmendConsultingContractRequest(null, null, null, 70_000, null, null));

        assertThat(agreement.getTotalAmount()).isEqualTo(70_000);
        assertThat(session.getPrice()).isEqualTo(70_000);
        // 비워 둔 칸은 그대로 둔다.
        assertThat(agreement.getTopic()).isEqualTo("회원탈퇴 경로를 봅니다");
        assertThat(agreement.getDetail()).isEqualTo("관리자 화면 접근 경로를 확인합니다");
        assertThat(session.getDurationMinutes()).isEqualTo(30);
        assertThat(reservation.getScheduledAt()).isEqualTo(FUTURE.toLocalDateTime());
    }

    @Test
    @DisplayName("상세 설명을 보내면 바꾸고 보내지 않으면 유지한다")
    void amendsDetailOnlyWhenGiven() {
        service.amend(
                CONTRACT_ID,
                CLIENT_ID,
                new AmendConsultingContractRequest(
                        null, null, null, null, "결제 실패 재현 방법도 확인합니다", null));

        assertThat(agreement.getDetail()).isEqualTo("결제 실패 재현 방법도 확인합니다");

        service.amend(
                CONTRACT_ID,
                CLIENT_ID,
                new AmendConsultingContractRequest(null, null, null, 70_000, null, null));

        assertThat(agreement.getDetail()).isEqualTo("결제 실패 재현 방법도 확인합니다");
    }

    @Test
    @DisplayName("수정 제안은 의뢰인도 보낸다")
    void clientMayAmend() {
        service.amend(
                CONTRACT_ID,
                CLIENT_ID,
                new AmendConsultingContractRequest(null, null, null, null, null, "금액을 낮춰 주세요"));

        ArgumentCaptor<TempMessage> captor = ArgumentCaptor.forClass(TempMessage.class);
        verify(tempMessageRepository).save(captor.capture());
        // 누가 남겼는지로 화면의 좌우가 갈린다.
        assertThat(captor.getValue().isClient()).isTrue();
    }

    @Test
    @DisplayName("서명한 뒤에는 고칠 수 없다")
    void rejectsAmendAfterSignature() {
        ReflectionTestUtils.setField(contract, "developerSignatureId", 5);

        assertThatThrownBy(() -> service.amend(
                        CONTRACT_ID,
                        CLIENT_ID,
                        new AmendConsultingContractRequest(null, null, null, 1, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ContractErrorCode.CONTRACT_ALREADY_SIGNED);
    }

    @Test
    @DisplayName("당사자가 아니면 계약이 있다는 것도 알리지 않는다")
    void hidesContractFromStranger() {
        assertThatThrownBy(() -> service.amend(
                        CONTRACT_ID,
                        STRANGER_ID,
                        new AmendConsultingContractRequest(null, null, null, 1, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_NOT_FOUND);
    }

    private static WriteConsultingContractRequest request(OffsetDateTime scheduledAt, int price) {
        return new WriteConsultingContractRequest(
                "회원탈퇴 경로를 봅니다", scheduledAt, 30, price, null, "확인할 것 있으면 적어주세요");
    }
}
