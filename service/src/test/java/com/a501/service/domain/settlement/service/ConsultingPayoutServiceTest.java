package com.a501.service.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.account.dto.PayoutAccount;
import com.a501.service.domain.account.service.AccountQueryService;
import com.a501.service.domain.consulting.dto.SessionSchedule;
import com.a501.service.domain.consulting.service.ConsultingSessionQueryService;
import com.a501.service.domain.contract.dto.ConsultingPayoutTarget;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.contract.service.ContractSettlementService;
import com.a501.service.domain.report.service.ReportQueryService;
import com.a501.service.domain.settlement.SettlementErrorCode;
import com.a501.service.domain.settlement.client.EscrowPayoutClient;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.EscrowLedger;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.LedgerEntry;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.PayoutRequest;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConsultingPayoutServiceTest {
    private static final Integer CONTRACT_ID = 120;
    private static final Integer AGREEMENT_ID = 11;
    private static final Integer CLIENT_ID = 7;
    private static final Integer DEVELOPER_ID = 9;
    private static final long ESCROW_ID = 301;
    private static final String REFERENCE = "SETTLEMENT:CONSULTING-120";
    private static final String KEY = "escrow:release:consulting-contract-120";
    private static final String TOKEN = "lock-token";

    /** 예약 09:00 에 60분짜리. 예약상 10:00 에 끝나고, 사흘 뒤 10:00 부터 지급한다. */
    private static final LocalDateTime SCHEDULED = LocalDateTime.of(2026, 9, 20, 9, 0);
    private static final LocalDateTime DUE = LocalDateTime.of(2026, 9, 23, 10, 0);

    private static final ConsultingPayoutTarget TARGET =
            new ConsultingPayoutTarget(CONTRACT_ID, AGREEMENT_ID, 50_000, CLIENT_ID, DEVELOPER_ID);
    private static final PayoutAccount ACCOUNT =
            new PayoutAccount("001", "0011234567890123", "komit-9");

    private final ContractQueryService contracts = mock(ContractQueryService.class);
    private final ContractSettlementService completion = mock(ContractSettlementService.class);
    private final ConsultingSessionQueryService sessions = mock(ConsultingSessionQueryService.class);
    private final ReportQueryService reports = mock(ReportQueryService.class);
    private final AccountQueryService accounts = mock(AccountQueryService.class);
    private final EscrowPayoutClient escrow = mock(EscrowPayoutClient.class);
    private final PayoutLock lock = mock(PayoutLock.class);

    private final ConsultingPayoutService service = new ConsultingPayoutService(
            contracts, completion, sessions, reports, accounts, escrow, lock);

    @BeforeEach
    void setUp() {
        when(sessions.findSchedules(AGREEMENT_ID)).thenReturn(List.of(endedSession()));
        when(reports.hasOpenReport(CONTRACT_ID)).thenReturn(false);
        when(accounts.findPayoutAccount(DEVELOPER_ID)).thenReturn(Optional.of(ACCOUNT));
        when(lock.tryAcquire(CONTRACT_ID)).thenReturn(Optional.of(TOKEN));
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("ACTIVE", false)));
        when(escrow.release(any())).thenReturn("COMPLETED");
    }

    @Test
    @DisplayName("예약 종료 뒤 사흘이 되기 전에는 Escrow 를 부르지 않는다 (R-CON-10)")
    void waitsForObjectionPeriod() {
        assertThat(service.payIfDue(TARGET, DUE.minusSeconds(1))).isEqualTo(PayoutOutcome.NOT_DUE);

        verify(escrow, never()).findByContract(any());
        verify(lock, never()).tryAcquire(any());
    }

    @Test
    @DisplayName("사흘이 되는 순간부터 지급한다. 기간은 실제 종료가 아니라 예약 종료부터 센다")
    void paysFromScheduledEndPlusThreeDays() {
        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.PAID);
    }

    @Test
    @DisplayName("지급 요청에 계약마다 고정된 멱등키·정산 식별자와 금융망 예금주명을 싣는다")
    void sendsFixedKeyAndHolderName() {
        service.payIfDue(TARGET, DUE);

        ArgumentCaptor<PayoutRequest> sent = ArgumentCaptor.forClass(PayoutRequest.class);
        verify(escrow).release(sent.capture());
        assertThat(sent.getValue()).isEqualTo(new PayoutRequest(
                ESCROW_ID, REFERENCE, BigInteger.valueOf(50_000), "001", "0011234567890123", "komit-9", KEY));
    }

    @Test
    @DisplayName("지급이 확정되면 계약에 완료를 적고 잠금을 푼다")
    void marksCompletedAndReleasesLock() {
        service.payIfDue(TARGET, DUE);

        verify(completion).completeConsultingPayout(AGREEMENT_ID);
        verify(lock).release(CONTRACT_ID, TOKEN);
    }

    @Test
    @DisplayName("Escrow 가 아직 처리 중이라고 답하면 완료를 적지 않는다")
    void keepsOpenWhileEscrowIsProcessing() {
        when(escrow.release(any())).thenReturn("PROCESSING");

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.SENT);
        verify(completion, never()).completeConsultingPayout(any());
    }

    @Test
    @DisplayName("열리지 않은 세션이 있으면 자동 지급하지 않는다 (R-CON-12)")
    void holdsWhenSessionNeverStarted() {
        when(sessions.findSchedules(AGREEMENT_ID))
                .thenReturn(List.of(new SessionSchedule(55, SCHEDULED, 60, null, null)));

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.SESSION_EXCEPTION);
        verify(escrow, never()).release(any());
    }

    @Test
    @DisplayName("종료가 확정되지 않은 세션이 있으면 자동 지급하지 않는다 (R-CON-12)")
    void holdsWhenSessionNeverEnded() {
        when(sessions.findSchedules(AGREEMENT_ID))
                .thenReturn(List.of(new SessionSchedule(55, SCHEDULED, 60, SCHEDULED, null)));

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.SESSION_EXCEPTION);
    }

    @Test
    @DisplayName("예약 시각을 모르는 세션이 있으면 기간을 셀 수 없어 지급하지 않는다")
    void skipsWhenScheduleUnknown() {
        when(sessions.findSchedules(AGREEMENT_ID)).thenReturn(List.of(
                endedSession(), new SessionSchedule(56, null, 60, null, null)));

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.NO_SCHEDULE);
    }

    @Test
    @DisplayName("세션이 여럿이면 가장 늦게 끝나는 세션부터 기간을 센다")
    void countsFromLastSession() {
        LocalDateTime later = SCHEDULED.plusDays(1);
        when(sessions.findSchedules(AGREEMENT_ID)).thenReturn(List.of(
                endedSession(), new SessionSchedule(56, later, 60, later, later.plusMinutes(60))));

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.NOT_DUE);
        assertThat(service.payIfDue(TARGET, DUE.plusDays(1))).isEqualTo(PayoutOutcome.PAID);
    }

    @Test
    @DisplayName("해결되지 않은 신고가 있으면 지급하지 않는다 (R-CON-11)")
    void holdsWhileReported() {
        when(reports.hasOpenReport(CONTRACT_ID)).thenReturn(true);

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.REPORTED);
        verify(escrow, never()).release(any());
    }

    @Test
    @DisplayName("개발자가 받을 계좌를 등록하지 않았으면 기다린다 (R-PAY-14)")
    void waitsForPayoutAccount() {
        when(accounts.findPayoutAccount(DEVELOPER_ID)).thenReturn(Optional.empty());

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.NO_ACCOUNT);
        verify(lock, never()).tryAcquire(any());
    }

    @Test
    @DisplayName("다른 서버가 잠금을 잡고 있으면 이번 회차는 넘긴다")
    void skipsWhenAnotherInstanceHoldsLock() {
        when(lock.tryAcquire(CONTRACT_ID)).thenReturn(Optional.empty());

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.BUSY);
        verify(escrow, never()).findByContract(any());
    }

    @Test
    @DisplayName("Escrow 에 예치가 없으면 지급하지 않는다")
    void skipsWhenNotDeposited() {
        when(escrow.findByContract("120")).thenReturn(Optional.empty());

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.NOT_DEPOSITED);
        verify(escrow, never()).release(any());
    }

    @Test
    @DisplayName("실패로 확정된 지급은 다시 부르지 않는다. 같은 키로 부르면 송금을 다시 시도하기 때문이다")
    void neverRetriesFailedPayout() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("ACTIVE", false,
                new LedgerEntry("RELEASE", "FAILED", REFERENCE))));

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.STOPPED);
        verify(escrow, never()).release(any());
        verify(completion, never()).completeConsultingPayout(any());
    }

    @Test
    @DisplayName("결과를 모르는 지급은 같은 멱등키로 다시 부른다 (R-PAY-12)")
    void retriesUnknownWithSameKey() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("ACTIVE", false,
                new LedgerEntry("RELEASE", "UNKNOWN", REFERENCE))));

        service.payIfDue(TARGET, DUE);

        ArgumentCaptor<PayoutRequest> sent = ArgumentCaptor.forClass(PayoutRequest.class);
        verify(escrow).release(sent.capture());
        assertThat(sent.getValue().idempotencyKey()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("Escrow 가 처리 중이면 겹쳐 부르지 않고 기다린다")
    void waitsWhileInFlight() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("ACTIVE", false,
                new LedgerEntry("RELEASE", "PROCESSING", REFERENCE))));

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.IN_PROGRESS);
        verify(escrow, never()).release(any());
    }

    @Test
    @DisplayName("지급과 수수료가 이미 끝났으면 부르지 않고 완료만 적는다")
    void recordsAlreadyCompletedPayout() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("COMPLETED", false,
                new LedgerEntry("DEPOSIT", "COMPLETED", "INITIAL"),
                new LedgerEntry("RELEASE", "COMPLETED", REFERENCE),
                new LedgerEntry("FEE", "COMPLETED", REFERENCE))));

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.PAID);
        verify(escrow, never()).release(any());
        verify(completion).completeConsultingPayout(AGREEMENT_ID);
    }

    @Test
    @DisplayName("지급은 끝났지만 수수료 결과를 모르면 같은 키로 다시 불러 마무리한다")
    void finishesPendingFeeWithSameKey() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("ACTIVE", false,
                new LedgerEntry("RELEASE", "COMPLETED", REFERENCE),
                new LedgerEntry("FEE", "UNKNOWN", REFERENCE))));

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.PAID);
        verify(escrow).release(any());
    }

    @Test
    @DisplayName("동결된 에스크로에는 지급을 부르지 않는다 (R-RPT-05)")
    void skipsWhileHeld() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("ACTIVE", true)));

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.HELD);
        verify(escrow, never()).release(any());
    }

    @Test
    @DisplayName("예치가 완료되지 않았거나 닫힌 에스크로에는 지급을 부르지 않는다")
    void skipsInactiveEscrow() {
        for (String status : List.of("DEPOSIT_PENDING", "REFUNDED", "CANCELLED", "COMPLETED")) {
            when(escrow.findByContract("120")).thenReturn(Optional.of(ledger(status, false)));

            assertThat(service.payIfDue(TARGET, DUE)).as(status).isEqualTo(PayoutOutcome.NOT_PAYABLE);
        }
        verify(escrow, never()).release(any());
    }

    @Test
    @DisplayName("결과를 모르면 완료를 적지 않고, 잠금은 푼다")
    void unknownResultKeepsContractOpen() {
        when(escrow.release(any())).thenThrow(new BusinessException(SettlementErrorCode.ESCROW_UNAVAILABLE));

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.UNKNOWN);
        verify(completion, never()).completeConsultingPayout(any());
        verify(lock).release(CONTRACT_ID, TOKEN);
    }

    @Test
    @DisplayName("Escrow 가 동결·거절로 답하면 그 갈래로 옮긴다")
    void mapsHeldAndRejected() {
        doThrow(new BusinessException(SettlementErrorCode.ESCROW_HELD)).when(escrow).release(any());
        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.HELD);

        doThrow(new BusinessException(SettlementErrorCode.PAYOUT_REJECTED)).when(escrow).release(any());
        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.REJECTED);
    }

    @Test
    @DisplayName("예치된 금액이 계약 금액과 다르면 지급하지 않고 사람이 확인하게 둔다")
    void stopsWhenDepositDiffersFromContractAmount() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(
                new EscrowLedger(ESCROW_ID, "ACTIVE", false, BigInteger.valueOf(40_000), List.of())));

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.AMOUNT_MISMATCH);
        verify(escrow, never()).release(any());
        verify(completion, never()).completeConsultingPayout(any());
    }

    @Test
    @DisplayName("다른 계약의 거래는 이 지급의 판단에 섞지 않는다")
    void ignoresOtherReferences() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("ACTIVE", false,
                new LedgerEntry("RELEASE", "FAILED", "SETTLEMENT:OTHER"))));

        assertThat(service.payIfDue(TARGET, DUE)).isEqualTo(PayoutOutcome.PAID);
    }

    @Test
    @DisplayName("한 계약에서 예외가 나도 다음 계약을 계속 본다")
    void oneFailureDoesNotStopRound() {
        ConsultingPayoutTarget broken = new ConsultingPayoutTarget(121, 12, 50_000, CLIENT_ID, DEVELOPER_ID);
        when(contracts.findUnpaidConsultingContracts()).thenReturn(List.of(broken, TARGET));
        when(sessions.findSchedules(12)).thenThrow(new IllegalStateException("boom"));
        service.payDue(DUE);

        verify(completion).completeConsultingPayout(AGREEMENT_ID);
    }

    private static SessionSchedule endedSession() {
        return new SessionSchedule(55, SCHEDULED, 60, SCHEDULED, SCHEDULED.plusMinutes(60));
    }

    private static EscrowLedger ledger(String status, boolean held, LedgerEntry... entries) {
        return new EscrowLedger(ESCROW_ID, status, held, BigInteger.valueOf(50_000), List.of(entries));
    }
}
