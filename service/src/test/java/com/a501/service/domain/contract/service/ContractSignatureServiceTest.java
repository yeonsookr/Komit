package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.entity.Reservation;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ReservationRepository;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.client.EscrowDepositClient;
import com.a501.service.domain.contract.dto.request.SignContractRequest;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.ContractSignature;
import com.a501.service.domain.contract.entity.ContractTemplate;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.ContractSignatureRepository;
import com.a501.service.domain.contract.repository.ContractTemplateRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 계약서 서명 규칙을 DB 없이 검증합니다.
 *
 * <p>양쪽이 모두 서명해야 계약이 성립하고 (`R-CON-01`), 같은 사람이 두 번 서명할 수는 없습니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractSignatureServiceTest {
    private static final Integer ROOM_ID = 92;
    private static final Integer CONTRACT_ID = 120;
    private static final Integer TEMPLATE_ID = 3;
    private static final Integer CLIENT_ID = 9001;
    private static final Integer DEVELOPER_USER_ID = 9101;
    private static final Integer STRANGER_ID = 9999;
    private static final Integer FILE_ID = 41;
    private static final Instant NOW = Instant.parse("2026-09-25T02:00:00Z");

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ContractSignatureRepository signatureRepository;
    @Mock
    private ContractTemplateRepository templateRepository;
    @Mock
    private RoomQueryService roomQueryService;
    @Mock
    private ContractDetailService contractDetailService;
    @Mock
    private ConsultingAgreementRepository consultingAgreementRepository;
    @Mock
    private ConsultingSessionRepository consultingSessionRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private EscrowDepositClient escrowDepositClient;

    private ContractSignatureService service;
    private Contract contract;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        service = new ContractSignatureService(
                contractRepository,
                signatureRepository,
                templateRepository,
                roomQueryService,
                contractDetailService,
                new ConsultingReservationConfirmation(
                        consultingAgreementRepository,
                        consultingSessionRepository,
                        reservationRepository,
                        escrowDepositClient),
                Clock.fixed(NOW, ZoneOffset.UTC));

        contract = Contract.of(ROOM_ID, Contract.ContractType.CONSULTING);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        ReflectionTestUtils.setField(contract, "templateId", TEMPLATE_ID);

        when(contractRepository.findWithLockByIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(contract));
        when(roomQueryService.findParties(ROOM_ID))
                .thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_USER_ID)));

        AtomicInteger signatureId = new AtomicInteger(500);
        when(signatureRepository.save(any(ContractSignature.class))).thenAnswer(call -> {
            ContractSignature saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", signatureId.incrementAndGet());
            return saved;
        });

        ContractTemplate template = org.mockito.Mockito.mock(ContractTemplate.class);
        when(template.getBody()).thenReturn("제1조 …");
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));

        ConsultingAgreement agreement = ConsultingAgreement.of(CONTRACT_ID, "인증 오류 확인", 50_000);
        ReflectionTestUtils.setField(agreement, "id", 70);
        ConsultingSession session = ConsultingSession.of(70, 50_000, 30);
        ReflectionTestUtils.setField(session, "id", 80);
        reservation = Reservation.of(80, LocalDateTime.of(2026, 9, 26, 5, 0));
        when(consultingAgreementRepository.findByContractIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(agreement));
        when(consultingSessionRepository.findAllByConsultingAgreementIdAndDeletedFalseOrderByIdAsc(70))
                .thenReturn(List.of(session));
        when(reservationRepository.findByConsultingSessionIdAndDeletedFalse(80))
                .thenReturn(Optional.of(reservation));
        when(escrowDepositClient.findByContract("120")).thenReturn(Optional.of(deposit(50_000)));
    }

    private static EscrowDepositClient.Deposit deposit(long amount) {
        return new EscrowDepositClient.Deposit(
                77L, "120", "ACTIVE", BigInteger.valueOf(amount), BigInteger.valueOf(amount));
    }

    @Test
    @DisplayName("대금이 예치된 컨설팅 계약이 양쪽 서명으로 성립하면 예약을 확정한다 (R-CON-22)")
    void confirmsReservationWhenConsultingConcludes() {
        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));
        service.sign(CONTRACT_ID, DEVELOPER_USER_ID, new SignContractRequest(42));

        assertThat(reservation.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("양쪽이 서명해도 대금을 예치하기 전이면 예약을 확정하지 않는다 (R-CON-22)")
    void keepsReservationProposedUntilDeposited() {
        when(escrowDepositClient.findByContract("120")).thenReturn(Optional.empty());

        service.sign(CONTRACT_ID, DEVELOPER_USER_ID, new SignContractRequest(42));
        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));

        assertThat(contract.isConcluded()).isTrue();
        assertThat(reservation.getStatus()).isEqualTo("PROPOSED");
    }

    @Test
    @DisplayName("예치가 끝나지 않았으면 예치 기록이 있어도 확정하지 않는다")
    void keepsReservationProposedWhileDepositPending() {
        when(escrowDepositClient.findByContract("120")).thenReturn(Optional.of(deposit(0)));

        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));
        service.sign(CONTRACT_ID, DEVELOPER_USER_ID, new SignContractRequest(42));

        assertThat(reservation.getStatus()).isEqualTo("PROPOSED");
    }

    @Test
    @DisplayName("성립하는 서명에서 예치 여부를 확인하지 못하면 서명도 실패한다")
    void failsSignatureWhenDepositCannotBeChecked() {
        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));
        when(escrowDepositClient.findByContract("120"))
                .thenThrow(new BusinessException(ContractErrorCode.ESCROW_UNAVAILABLE));

        assertThatThrownBy(() -> service.sign(CONTRACT_ID, DEVELOPER_USER_ID, new SignContractRequest(42)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.ESCROW_UNAVAILABLE);
    }

    @Test
    @DisplayName("한쪽만 서명한 동안에는 예치를 묻지 않고 예약이 제안 상태로 남는다")
    void keepsReservationProposedUntilBothSign() {
        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));

        assertThat(reservation.getStatus()).isEqualTo("PROPOSED");
        verify(escrowDepositClient, never()).findByContract(any());
    }

    @Test
    @DisplayName("개발 계약은 예약을 찾지 않는다")
    void skipsReservationForDevelopment() {
        ReflectionTestUtils.setField(contract, "contractType", Contract.ContractType.DEVELOPMENT.name());

        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));
        service.sign(CONTRACT_ID, DEVELOPER_USER_ID, new SignContractRequest(42));

        assertThat(contract.isConcluded()).isTrue();
        verify(consultingAgreementRepository, never()).findByContractIdAndDeletedFalse(any());
        verify(escrowDepositClient, never()).findByContract(any());
    }

    @Test
    @DisplayName("의뢰인이 취소한 계약에는 개발자가 뒤늦게 서명해도 받지 않는다 (R-CON-21)")
    void rejectsSignatureOnCancelledContract() {
        contract.cancel();

        assertThatThrownBy(() -> service.sign(CONTRACT_ID, DEVELOPER_USER_ID, new SignContractRequest(FILE_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_CANCELLED);
        assertThat(contract.getDeveloperSignatureId()).isNull();
        assertThat(contract.isConcluded()).isFalse();
    }

    @Test
    @DisplayName("한쪽만 서명하면 협의중으로 남는다")
    void staysNegotiatingUntilBothSign() {
        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));

        assertThat(contract.getClientSignatureId()).isNotNull();
        assertThat(contract.getDeveloperSignatureId()).isNull();
        assertThat(contract.getStatus()).isEqualTo("NEGOTIATING");
        assertThat(contract.isConcluded()).isFalse();
        // 아직 합의하지 않았으므로 문구를 굳히지 않는다.
        assertThat(contract.getContractText()).isNull();
    }

    @Test
    @DisplayName("양쪽이 서명하면 계약이 성립한다")
    void concludesWhenBothSign() {
        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));
        service.sign(CONTRACT_ID, DEVELOPER_USER_ID, new SignContractRequest(42));

        assertThat(contract.isConcluded()).isTrue();
        assertThat(contract.getStatus()).isEqualTo("SIGNED");
    }

    @Test
    @DisplayName("성립한 순간의 계약 문구를 그대로 굳힌다")
    void freezesContractText() {
        service.sign(CONTRACT_ID, DEVELOPER_USER_ID, new SignContractRequest(FILE_ID));
        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(42));

        // 나중에 양식이 바뀌어도 이 계약은 그때 합의한 글이어야 한다.
        assertThat(contract.getContractText()).isEqualTo("제1조 …");
    }

    @Test
    @DisplayName("양식이 없으면 문구를 비워 두고 서명은 그대로 받는다")
    void signsEvenWithoutTemplate() {
        ReflectionTestUtils.setField(contract, "templateId", null);

        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));
        service.sign(CONTRACT_ID, DEVELOPER_USER_ID, new SignContractRequest(42));

        assertThat(contract.isConcluded()).isTrue();
        assertThat(contract.getContractText()).isNull();
    }

    @Test
    @DisplayName("서명 시각과 그림을 남긴다")
    void recordsSignature() {
        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));

        org.mockito.ArgumentCaptor<ContractSignature> captor =
                org.mockito.ArgumentCaptor.forClass(ContractSignature.class);
        verify(signatureRepository).save(captor.capture());
        ContractSignature saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(CLIENT_ID);
        assertThat(saved.getSignatureImageFileId()).isEqualTo(FILE_ID);
        assertThat(saved.isSigned()).isTrue();
        assertThat(saved.getSignedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("같은 사람이 두 번 서명할 수는 없다")
    void rejectsSecondSignatureFromSamePerson() {
        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));

        assertThatThrownBy(
                        () -> service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(42)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.ALREADY_SIGNED_BY_ME);
    }

    @Test
    @DisplayName("당사자가 아니면 계약이 있다는 것도 알리지 않는다")
    void hidesContractFromStranger() {
        assertThatThrownBy(
                        () -> service.sign(CONTRACT_ID, STRANGER_ID, new SignContractRequest(FILE_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_NOT_FOUND);
        verify(signatureRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 계약은 404 다")
    void rejectsMissingContract() {
        when(contractRepository.findWithLockByIdAndDeletedFalse(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sign(404, CLIENT_ID, new SignContractRequest(FILE_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_NOT_FOUND);
    }
}
