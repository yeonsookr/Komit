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
import com.a501.service.domain.settlement.SettlementErrorCode;
import com.a501.service.domain.settlement.client.EscrowPayoutClient;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.EscrowLedger;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.LedgerEntry;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.RefundRequest;
import com.a501.service.domain.settlement.service.EscrowRefundService.RefundOutcome;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 취소된 계약의 예치금 환불 (`R-CON-21`, `R-PAY-04`). */
class EscrowRefundServiceTest {
    private static final Integer CONTRACT_ID = 120;
    private static final Integer CLIENT_ID = 7;
    private static final long ESCROW_ID = 301;
    private static final String REFERENCE = "SETTLEMENT:CANCEL-120";
    private static final String KEY = "escrow:refund:cancel-contract-120";
    private static final PayoutAccount ISSUED = new PayoutAccount("001", "0011234567890123", "komit-7");

    private final EscrowPayoutClient escrow = mock(EscrowPayoutClient.class);
    private final AccountQueryService accounts = mock(AccountQueryService.class);
    private final PayoutLock lock = mock(PayoutLock.class);
    private final EscrowRefundService service = new EscrowRefundService(escrow, accounts, lock);

    @BeforeEach
    void setUp() {
        when(lock.tryAcquire(CONTRACT_ID)).thenReturn(Optional.of("token"));
        when(accounts.findRefundAccount(CLIENT_ID)).thenReturn(Optional.of(ISSUED));
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("ACTIVE", false, 50_000)));
        when(escrow.refund(any())).thenReturn("COMPLETED");
    }

    @Test
    @DisplayName("남은 예치금 전액을 발급 계좌로, 계약마다 고정된 키로 돌려준다")
    void refundsWholeBalanceToIssuedAccount() {
        assertThat(service.refundAll(CONTRACT_ID, CLIENT_ID)).isEqualTo(RefundOutcome.REFUNDED);

        ArgumentCaptor<RefundRequest> sent = ArgumentCaptor.forClass(RefundRequest.class);
        verify(escrow).refund(sent.capture());
        assertThat(sent.getValue()).isEqualTo(new RefundRequest(
                ESCROW_ID, REFERENCE, BigInteger.valueOf(50_000), "001", "0011234567890123", "komit-7", KEY));
        verify(lock).release(CONTRACT_ID, "token");
    }

    @Test
    @DisplayName("예치가 없으면 돌려줄 것이 없다")
    void nothingWithoutDeposit() {
        when(escrow.findByContract("120")).thenReturn(Optional.empty());

        assertThat(service.refundAll(CONTRACT_ID, CLIENT_ID)).isEqualTo(RefundOutcome.NOTHING_TO_REFUND);
        verify(escrow, never()).refund(any());
    }

    @Test
    @DisplayName("이미 돌려줬으면 다시 부르지 않는다")
    void doesNotRefundTwice() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("REFUNDED", false, 0,
                new LedgerEntry("REFUND", "COMPLETED", REFERENCE))));

        assertThat(service.refundAll(CONTRACT_ID, CLIENT_ID)).isEqualTo(RefundOutcome.REFUNDED);
        verify(escrow, never()).refund(any());
    }

    @Test
    @DisplayName("실패로 확정된 환불은 다시 부르지 않는다. 같은 키로 부르면 송금을 다시 시도하기 때문이다")
    void neverRetriesFailedRefund() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("ACTIVE", false, 50_000,
                new LedgerEntry("REFUND", "FAILED", REFERENCE))));

        assertThat(service.refundAll(CONTRACT_ID, CLIENT_ID)).isEqualTo(RefundOutcome.BLOCKED);
        verify(escrow, never()).refund(any());
    }

    @Test
    @DisplayName("결과를 모르는 환불은 같은 키로 다시 부른다")
    void retriesUnknownWithSameKey() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("ACTIVE", false, 50_000,
                new LedgerEntry("REFUND", "UNKNOWN", REFERENCE))));

        service.refundAll(CONTRACT_ID, CLIENT_ID);

        ArgumentCaptor<RefundRequest> sent = ArgumentCaptor.forClass(RefundRequest.class);
        verify(escrow).refund(sent.capture());
        assertThat(sent.getValue().idempotencyKey()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("처리 중이면 겹쳐 부르지 않고 기다린다")
    void waitsWhileInFlight() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("ACTIVE", false, 50_000,
                new LedgerEntry("REFUND", "PROCESSING", REFERENCE))));

        assertThat(service.refundAll(CONTRACT_ID, CLIENT_ID)).isEqualTo(RefundOutcome.PENDING);
        verify(escrow, never()).refund(any());
    }

    @Test
    @DisplayName("예치가 아직 확정되지 않았으면 기다린다. 확정된 뒤 다시 부르면 돌려준다")
    void waitsForUnresolvedDeposit() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("DEPOSIT_PENDING", false, 0)));

        assertThat(service.refundAll(CONTRACT_ID, CLIENT_ID)).isEqualTo(RefundOutcome.PENDING);
        verify(escrow, never()).refund(any());
    }

    @Test
    @DisplayName("예치 전에 취소된 에스크로는 돌려줄 것이 없다")
    void nothingForCancelledEscrow() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("CANCELLED", false, 0)));

        assertThat(service.refundAll(CONTRACT_ID, CLIENT_ID)).isEqualTo(RefundOutcome.NOTHING_TO_REFUND);
    }

    @Test
    @DisplayName("동결됐거나 지급으로 닫힌 에스크로는 사람이 본다")
    void blocksHeldOrClosedEscrow() {
        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("ACTIVE", true, 50_000)));
        assertThat(service.refundAll(CONTRACT_ID, CLIENT_ID)).isEqualTo(RefundOutcome.BLOCKED);

        when(escrow.findByContract("120")).thenReturn(Optional.of(ledger("COMPLETED", false, 0)));
        assertThat(service.refundAll(CONTRACT_ID, CLIENT_ID)).isEqualTo(RefundOutcome.BLOCKED);
        verify(escrow, never()).refund(any());
    }

    @Test
    @DisplayName("Escrow 결과를 모르면 기다림, 거절이면 사람이 본다")
    void mapsEscrowFailures() {
        doThrow(new BusinessException(SettlementErrorCode.ESCROW_UNAVAILABLE)).when(escrow).refund(any());
        assertThat(service.refundAll(CONTRACT_ID, CLIENT_ID)).isEqualTo(RefundOutcome.PENDING);

        doThrow(new BusinessException(SettlementErrorCode.PAYOUT_REJECTED)).when(escrow).refund(any());
        assertThat(service.refundAll(CONTRACT_ID, CLIENT_ID)).isEqualTo(RefundOutcome.BLOCKED);
    }

    @Test
    @DisplayName("다른 곳이 이 계약을 처리 중이면 기다린다")
    void waitsWhenLocked() {
        when(lock.tryAcquire(CONTRACT_ID)).thenReturn(Optional.empty());

        assertThat(service.refundAll(CONTRACT_ID, CLIENT_ID)).isEqualTo(RefundOutcome.PENDING);
        verify(escrow, never()).findByContract(any());
    }

    private static EscrowLedger ledger(String status, boolean held, long balance, LedgerEntry... entries) {
        return new EscrowLedger(ESCROW_ID, status, held, BigInteger.valueOf(50_000), BigInteger.valueOf(balance),
                List.of(entries));
    }
}
