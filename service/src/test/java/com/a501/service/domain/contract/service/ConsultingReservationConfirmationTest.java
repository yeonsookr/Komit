package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.entity.Reservation;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ReservationRepository;
import com.a501.service.domain.contract.client.EscrowDepositClient;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 예치한 뒤 세션 예약을 확정하는 규칙 (`R-CON-22`). 서명한 뒤는 {@link ContractSignatureServiceTest} 가 본다. */
class ConsultingReservationConfirmationTest {
    private static final Integer CONTRACT_ID = 120;

    private final ConsultingAgreementRepository agreements = mock(ConsultingAgreementRepository.class);
    private final ConsultingSessionRepository sessions = mock(ConsultingSessionRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final ConsultingReservationConfirmation confirmation = new ConsultingReservationConfirmation(
            agreements, sessions, reservations, mock(EscrowDepositClient.class));

    private Contract contract;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        contract = Contract.of(92, Contract.ContractType.CONSULTING);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        ConsultingAgreement agreement = ConsultingAgreement.of(CONTRACT_ID, "인증 오류 확인", 50_000);
        ReflectionTestUtils.setField(agreement, "id", 70);
        ConsultingSession session = ConsultingSession.of(70, 50_000, 30);
        ReflectionTestUtils.setField(session, "id", 80);
        reservation = Reservation.of(80, LocalDateTime.of(2026, 9, 26, 5, 0));
        when(agreements.findByContractIdAndDeletedFalse(CONTRACT_ID)).thenReturn(Optional.of(agreement));
        when(sessions.findAllByConsultingAgreementIdAndDeletedFalseOrderByIdAsc(70)).thenReturn(List.of(session));
        when(reservations.findByConsultingSessionIdAndDeletedFalse(80)).thenReturn(Optional.of(reservation));
    }

    private static EscrowDepositClient.Deposit deposit(long amount) {
        return new EscrowDepositClient.Deposit(
                77L, "120", "ACTIVE", BigInteger.valueOf(amount), BigInteger.valueOf(amount));
    }

    @Test
    @DisplayName("개발자가 먼저 서명한 계약은 예치가 끝나면 확정한다")
    void confirmsWhenDepositCompletesConcludedContract() {
        contract.sign(false, 1, null);
        contract.sign(true, 2, null);

        confirmation.afterDeposited(contract, deposit(50_000));

        assertThat(reservation.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("개발자가 아직 서명하지 않았으면 예치해도 확정하지 않는다")
    void waitsForDeveloperSignature() {
        contract.sign(true, 2, null);

        confirmation.afterDeposited(contract, deposit(50_000));

        assertThat(reservation.getStatus()).isEqualTo("PROPOSED");
    }

    @Test
    @DisplayName("예치 결과가 아직 나오지 않았으면 확정하지 않는다")
    void waitsForPendingDeposit() {
        contract.sign(false, 1, null);
        contract.sign(true, 2, null);

        confirmation.afterDeposited(contract, deposit(0));

        assertThat(reservation.getStatus()).isEqualTo("PROPOSED");
    }
}
