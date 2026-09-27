package com.a501.service.domain.settlement.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.account.dto.PayoutAccount;
import com.a501.service.domain.settlement.SettlementErrorCode;
import com.a501.service.domain.settlement.client.EscrowPayoutClient;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.EscrowLedger;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.LedgerEntry;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.PayoutRequest;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Escrow 에 계약 대금 지급을 부탁하고 결과를 판정한다. 컨설팅 지급과 개발 지급이 함께 쓴다.
 *
 * <p>언제 지급할지는 부르는 쪽이 정한다. 여기서는 Escrow 원장을 읽어 이번 회차에 할 일을 고르고, 필요하면 같은 멱등키로
 * 지급을 부탁한다. 지급액과 수수료 이체가 모두 확정되면 {@code onPaid} 를 부른다. (`R-PAY-07`, `R-PAY-12`)
 */
final class EscrowRelease {
    private static final String RELEASE = "RELEASE";
    private static final String FEE = "FEE";
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";
    private static final String ACTIVE = "ACTIVE";
    private static final List<String> IN_FLIGHT = List.of("PENDING", "PROCESSING");

    private EscrowRelease() {
    }

    /**
     * 지급 한 건.
     *
     * @param contractId 계약 id. Escrow 는 계약 id 로 에스크로를 찾는다
     * @param referenceId 계약마다 하나뿐인 정산 식별자
     * @param idempotencyKey 계약마다 하나뿐인 지급 멱등키
     * @param amount 수수료를 떼기 전 지급 승인 총액
     */
    record Request(Integer contractId, String referenceId, String idempotencyKey, int amount) {
        Request {
            Objects.requireNonNull(contractId, "contractId");
            Objects.requireNonNull(referenceId, "referenceId");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        }
    }

    static PayoutOutcome pay(EscrowPayoutClient escrow, Request request, PayoutAccount account, Runnable onPaid) {
        try {
            Optional<EscrowLedger> ledger = escrow.findByContract(String.valueOf(request.contractId()));
            if (ledger.isEmpty()) {
                return PayoutOutcome.NOT_DEPOSITED;
            }
            BigInteger amount = BigInteger.valueOf(request.amount());
            PayoutOutcome decision = decide(ledger.get(), request.referenceId(), amount);
            if (decision == PayoutOutcome.PAID) {
                onPaid.run();
                return PayoutOutcome.PAID;
            }
            if (decision != PayoutOutcome.SENT) {
                return decision;
            }

            String status = escrow.release(new PayoutRequest(
                    ledger.get().escrowId(),
                    request.referenceId(),
                    amount,
                    account.bankCode(),
                    account.accountNo(),
                    account.holderName(),
                    request.idempotencyKey()));
            if (COMPLETED.equals(status)) {
                onPaid.run();
                return PayoutOutcome.PAID;
            }
            return PayoutOutcome.SENT;
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == SettlementErrorCode.ESCROW_HELD) {
                return PayoutOutcome.HELD;
            }
            if (exception.getErrorCode() == SettlementErrorCode.PAYOUT_REJECTED) {
                return PayoutOutcome.REJECTED;
            }
            return PayoutOutcome.UNKNOWN;
        }
    }

    /**
     * Escrow 원장을 보고 이번 회차에 할 일을 정한다.
     *
     * <ul>
     *   <li>이 정산의 거래가 하나라도 {@code FAILED} 면 멈춘다. Escrow 는 실패한 정산을 같은 키로 다시 받으면
     *       송금을 다시 시도하므로, 돈이 한 번 나갔을 수도 있는 건을 자동으로 되풀이하지 않는다.
     *   <li>처리 중({@code PENDING}·{@code PROCESSING})이면 기다린다. 겹쳐 부르지 않는다.
     *   <li>지급과 수수료가 모두 {@code COMPLETED} 면 끝났다.
     *   <li>예치된 금액이 계약 금액과 다르면 멈춘다. 의뢰인 서명 뒤에만 예치하고 서명 뒤에는 계약서를 고칠 수 없어
     *       원래 어긋나지 않는다(`R-CON-20`). 그래도 어긋났다면 그대로 부를 때 잔액 부족으로 거절되거나, 일부만 나가고
     *       남은 돈이 묶인 채 완료로 보이므로 사람이 확인한다. (`R-PAY-01`)
     *   <li>그 밖에(아직 없음, {@code UNKNOWN}) 동결되지 않은 {@code ACTIVE} 에스크로면 같은 멱등키로 부른다.
     * </ul>
     */
    static PayoutOutcome decide(EscrowLedger ledger, String referenceId, BigInteger amount) {
        List<LedgerEntry> mine = ledger.transactions().stream()
                .filter(entry -> referenceId.equals(entry.referenceId()))
                .filter(entry -> RELEASE.equals(entry.type()) || FEE.equals(entry.type()))
                .toList();
        if (mine.stream().anyMatch(entry -> FAILED.equals(entry.status()))) {
            return PayoutOutcome.STOPPED;
        }
        if (mine.stream().anyMatch(entry -> IN_FLIGHT.contains(entry.status()))) {
            return PayoutOutcome.IN_PROGRESS;
        }
        boolean released = mine.stream()
                .anyMatch(entry -> RELEASE.equals(entry.type()) && COMPLETED.equals(entry.status()));
        if (released && mine.stream().allMatch(entry -> COMPLETED.equals(entry.status()))) {
            return PayoutOutcome.PAID;
        }
        if (!ACTIVE.equals(ledger.status())) {
            return PayoutOutcome.NOT_PAYABLE;
        }
        if (ledger.held()) {
            return PayoutOutcome.HELD;
        }
        if (!amount.equals(ledger.depositedAmount())) {
            return PayoutOutcome.AMOUNT_MISMATCH;
        }
        return PayoutOutcome.SENT;
    }
}
