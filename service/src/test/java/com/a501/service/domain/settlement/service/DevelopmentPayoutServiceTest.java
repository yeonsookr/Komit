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
import com.a501.service.domain.contract.dto.DevelopmentPayoutTarget;
import com.a501.service.domain.contract.service.ContractSettlementService;
import com.a501.service.domain.contract.service.DevelopmentPayoutQueryService;
import com.a501.service.domain.report.service.ReportQueryService;
import com.a501.service.domain.settlement.SettlementErrorCode;
import com.a501.service.domain.settlement.client.EscrowPayoutClient;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.EscrowLedger;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.LedgerEntry;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.PayoutRequest;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 개발 계약 전액 지급의 조건·요청·완료 기록을 본다. (R-PAY-01, R-PAY-07, R-PAY-12, R-QA-06) */
class DevelopmentPayoutServiceTest {
    private static final Integer CONTRACT_ID = 130;
    private static final Integer DEVELOPMENT_ID = 51;
    private static final Integer CLIENT_ID = 7;
    private static final Integer DEVELOPER_ID = 12;
    private static final long ESCROW_ID = 302;
    private static final int AMOUNT = 650_000;
    private static final String REFERENCE = "SETTLEMENT:DEVELOPMENT-130";
    private static final String KEY = "escrow:release:development-contract-130";
    private static final String TOKEN = "lock-token";

    private static final DevelopmentPayoutTarget TARGET =
            new DevelopmentPayoutTarget(CONTRACT_ID, DEVELOPMENT_ID, AMOUNT, CLIENT_ID, DEVELOPER_ID);
    private static final PayoutAccount ACCOUNT = new PayoutAccount("001", "0011234567890123", "komit-12");

    private final DevelopmentPayoutQueryService targets = mock(DevelopmentPayoutQueryService.class);
    private final ContractSettlementService completion = mock(ContractSettlementService.class);
    private final ReportQueryService reports = mock(ReportQueryService.class);
    private final AccountQueryService accounts = mock(AccountQueryService.class);
    private final EscrowPayoutClient escrow = mock(EscrowPayoutClient.class);
    private final PayoutLock lock = mock(PayoutLock.class);

    private final DevelopmentPayoutService service =
            new DevelopmentPayoutService(targets, completion, reports, accounts, escrow, lock);

    @BeforeEach
    void setUp() {
        when(reports.hasOpenReport(CONTRACT_ID)).thenReturn(false);
        when(accounts.findPayoutAccount(DEVELOPER_ID)).thenReturn(Optional.of(ACCOUNT));
        when(lock.tryAcquire(CONTRACT_ID)).thenReturn(Optional.of(TOKEN));
        when(escrow.findByContract("130")).thenReturn(Optional.of(ledger("ACTIVE", false)));
        when(escrow.release(any())).thenReturn("COMPLETED");
    }

    @Test
    @DisplayName("계약 총액을 한 번에, 계약마다 고정된 개발용 정산 식별자와 멱등키로 부탁한다")
    void sendsTotalAmountWithDevelopmentKeys() {
        assertThat(service.payIfAllowed(TARGET)).isEqualTo(PayoutOutcome.PAID);

        ArgumentCaptor<PayoutRequest> sent = ArgumentCaptor.forClass(PayoutRequest.class);
        verify(escrow).release(sent.capture());
        assertThat(sent.getValue()).isEqualTo(new PayoutRequest(
                ESCROW_ID, REFERENCE, BigInteger.valueOf(AMOUNT), "001", "0011234567890123", "komit-12", KEY));
    }

    @Test
    @DisplayName("지급액과 수수료가 확정되면 개발 계약에 완료를 적고 잠금을 푼다")
    void completesTheDevelopmentContractAfterPayout() {
        service.payIfAllowed(TARGET);

        verify(completion).completeDevelopmentPayout(DEVELOPMENT_ID);
        verify(lock).release(CONTRACT_ID, TOKEN);
    }

    @Test
    @DisplayName("지급을 부탁만 했고 확정되지 않았으면 완료를 적지 않는다")
    void doesNotCompleteWhileProcessing() {
        when(escrow.release(any())).thenReturn("PROCESSING");

        assertThat(service.payIfAllowed(TARGET)).isEqualTo(PayoutOutcome.SENT);
        verify(completion, never()).completeDevelopmentPayout(any());
    }

    @Test
    @DisplayName("지급은 끝났고 수수료가 아직이면 완료가 아니다. 결과를 모르면 같은 멱등키로 다시 부른다")
    void releaseAloneIsNotCompletion() {
        when(escrow.findByContract("130")).thenReturn(Optional.of(ledger("ACTIVE", false,
                new LedgerEntry("RELEASE", "COMPLETED", REFERENCE),
                new LedgerEntry("FEE", "UNKNOWN", REFERENCE))));
        when(escrow.release(any())).thenReturn("UNKNOWN");

        assertThat(service.payIfAllowed(TARGET)).isEqualTo(PayoutOutcome.SENT);
        ArgumentCaptor<PayoutRequest> sent = ArgumentCaptor.forClass(PayoutRequest.class);
        verify(escrow).release(sent.capture());
        assertThat(sent.getValue().idempotencyKey()).isEqualTo(KEY);
        verify(completion, never()).completeDevelopmentPayout(any());
    }

    @Test
    @DisplayName("원장에 지급과 수수료가 모두 확정돼 있으면 다시 부르지 않고 완료만 적는다")
    void alreadyPaidIsOnlyRecorded() {
        when(escrow.findByContract("130")).thenReturn(Optional.of(ledger("COMPLETED", false,
                new LedgerEntry("RELEASE", "COMPLETED", REFERENCE),
                new LedgerEntry("FEE", "COMPLETED", REFERENCE))));

        assertThat(service.payIfAllowed(TARGET)).isEqualTo(PayoutOutcome.PAID);
        verify(escrow, never()).release(any());
        verify(completion).completeDevelopmentPayout(DEVELOPMENT_ID);
    }

    @Test
    @DisplayName("컨설팅 정산 식별자의 거래는 이 계약의 지급으로 보지 않는다")
    void ignoresOtherReferences() {
        when(escrow.findByContract("130")).thenReturn(Optional.of(ledger("ACTIVE", false,
                new LedgerEntry("RELEASE", "COMPLETED", "SETTLEMENT:CONSULTING-130"),
                new LedgerEntry("FEE", "COMPLETED", "SETTLEMENT:CONSULTING-130"))));

        service.payIfAllowed(TARGET);

        verify(escrow).release(any());
    }

    @Test
    @DisplayName("해결되지 않은 신고가 있으면 Escrow 를 부르지 않는다 (R-QA-02)")
    void holdsWhenReported() {
        when(reports.hasOpenReport(CONTRACT_ID)).thenReturn(true);

        assertThat(service.payIfAllowed(TARGET)).isEqualTo(PayoutOutcome.REPORTED);
        verify(escrow, never()).findByContract(any());
        verify(lock, never()).tryAcquire(any());
    }

    @Test
    @DisplayName("동결된 에스크로는 부르지 않는다 (R-RPT-05)")
    void holdsWhenFrozen() {
        when(escrow.findByContract("130")).thenReturn(Optional.of(ledger("ACTIVE", true)));

        assertThat(service.payIfAllowed(TARGET)).isEqualTo(PayoutOutcome.HELD);
        verify(escrow, never()).release(any());
    }

    @Test
    @DisplayName("예치 금액이 계약 금액과 다르면 멈춘다")
    void stopsOnAmountMismatch() {
        when(escrow.findByContract("130")).thenReturn(Optional.of(new EscrowLedger(
                ESCROW_ID, "ACTIVE", false, BigInteger.valueOf(600_000), BigInteger.valueOf(600_000), List.of())));

        assertThat(service.payIfAllowed(TARGET)).isEqualTo(PayoutOutcome.AMOUNT_MISMATCH);
        verify(escrow, never()).release(any());
    }

    @Test
    @DisplayName("받을 계좌가 없거나 금액이 없으면 기다린다 (R-PAY-14)")
    void waitsForAccountAndAmount() {
        when(accounts.findPayoutAccount(DEVELOPER_ID)).thenReturn(Optional.empty());
        assertThat(service.payIfAllowed(TARGET)).isEqualTo(PayoutOutcome.NO_ACCOUNT);

        assertThat(service.payIfAllowed(new DevelopmentPayoutTarget(CONTRACT_ID, DEVELOPMENT_ID, 0, CLIENT_ID, DEVELOPER_ID)))
                .isEqualTo(PayoutOutcome.NO_AMOUNT);
        verify(escrow, never()).findByContract(any());
    }

    @Test
    @DisplayName("다른 서버가 잠갔으면 건너뛴다")
    void skipsWhenBusy() {
        when(lock.tryAcquire(CONTRACT_ID)).thenReturn(Optional.empty());

        assertThat(service.payIfAllowed(TARGET)).isEqualTo(PayoutOutcome.BUSY);
        verify(escrow, never()).findByContract(any());
    }

    @Test
    @DisplayName("Escrow 에 닿지 못하면 결과를 모른다고 두고 잠금은 푼다")
    void unknownWhenEscrowIsUnavailable() {
        when(escrow.release(any())).thenThrow(new BusinessException(SettlementErrorCode.ESCROW_UNAVAILABLE));

        assertThat(service.payIfAllowed(TARGET)).isEqualTo(PayoutOutcome.UNKNOWN);
        verify(completion, never()).completeDevelopmentPayout(any());
        verify(lock).release(CONTRACT_ID, TOKEN);
    }

    @Test
    @DisplayName("한 계약이 실패해도 회차의 나머지 계약을 계속 본다")
    void oneFailureDoesNotStopTheRound() {
        DevelopmentPayoutTarget other = new DevelopmentPayoutTarget(131, 52, AMOUNT, CLIENT_ID, DEVELOPER_ID);
        when(targets.findUnpaidDevelopmentContracts()).thenReturn(List.of(TARGET, other));
        doThrow(new IllegalStateException("db")).when(reports).hasOpenReport(CONTRACT_ID);
        when(lock.tryAcquire(131)).thenReturn(Optional.of(TOKEN));
        when(escrow.findByContract("131")).thenReturn(Optional.of(ledger("ACTIVE", false)));

        service.payDue();

        verify(completion).completeDevelopmentPayout(52);
    }

    private static EscrowLedger ledger(String status, boolean held, LedgerEntry... entries) {
        return new EscrowLedger(ESCROW_ID, status, held, BigInteger.valueOf(AMOUNT), BigInteger.valueOf(AMOUNT), List.of(entries));
    }
}
