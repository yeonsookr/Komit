package com.a501.service.domain.settlement.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.account.dto.PayoutAccount;
import com.a501.service.domain.account.service.AccountQueryService;
import com.a501.service.domain.settlement.SettlementErrorCode;
import com.a501.service.domain.settlement.client.EscrowPayoutClient;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.EscrowLedger;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.LedgerEntry;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.RefundRequest;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 취소된 계약의 예치금을 의뢰인에게 돌려준다. (`R-CON-21`, `R-PAY-04`)
 *
 * <p>지급({@link ConsultingPayoutService})과 같은 원칙을 쓴다. <b>환불 상태를 저장하지 않고</b> 멱등키와 정산 식별자를
 * 계약 id 로 정해 두며, 무엇을 할지는 매번 Escrow 의 거래 목록을 읽어 정한다. 같은 계약의 지급과 환불이 겹치지 않도록
 * 같은 {@link PayoutLock} 을 잡는다.
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> Escrow 를 부르는 동안 DB 연결을 붙잡지 않는다. (BACKEND_CONVENTIONS 16장)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowRefundService {
    private static final String REFUND = "REFUND";
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";
    private static final String ACTIVE = "ACTIVE";
    private static final List<String> IN_FLIGHT = List.of("PENDING", "PROCESSING");

    private final EscrowPayoutClient escrow;
    private final AccountQueryService accountQueryService;
    private final PayoutLock lock;

    /**
     * 이 계약에 남은 예치금 전액을 의뢰인의 발급 계좌로 돌려준다. 여러 번 불러도 돈은 한 번만 나간다.
     *
     * @param contractId Komit 계약 id
     * @param clientId 돈을 돌려받을 의뢰인
     * @return 이번 호출 뒤의 상태
     */
    public RefundOutcome refundAll(Integer contractId, Integer clientId) {
        Optional<String> token = lock.tryAcquire(contractId);
        if (token.isEmpty()) {
            return RefundOutcome.PENDING;
        }
        try {
            return refund(contractId, clientId);
        } finally {
            lock.release(contractId, token.get());
        }
    }

    private RefundOutcome refund(Integer contractId, Integer clientId) {
        String referenceId = referenceIdOf(contractId);
        try {
            Optional<EscrowLedger> ledger = escrow.findByContract(String.valueOf(contractId));
            if (ledger.isEmpty()) {
                return RefundOutcome.NOTHING_TO_REFUND;
            }
            RefundOutcome decision = decide(ledger.get(), referenceId);
            if (decision != RefundOutcome.SEND) {
                warnIfBlocked(contractId, decision);
                return decision;
            }
            Optional<PayoutAccount> account = accountQueryService.findRefundAccount(clientId);
            if (account.isEmpty()) {
                warnIfBlocked(contractId, RefundOutcome.BLOCKED);
                return RefundOutcome.BLOCKED;
            }

            String status = escrow.refund(new RefundRequest(
                    ledger.get().escrowId(),
                    referenceId,
                    ledger.get().balance(),
                    account.get().bankCode(),
                    account.get().accountNo(),
                    account.get().holderName(),
                    idempotencyKeyOf(contractId)));
            return COMPLETED.equals(status) ? RefundOutcome.REFUNDED : RefundOutcome.PENDING;
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == SettlementErrorCode.ESCROW_UNAVAILABLE) {
                return RefundOutcome.PENDING;
            }
            warnIfBlocked(contractId, RefundOutcome.BLOCKED);
            return RefundOutcome.BLOCKED;
        }
    }

    /**
     * Escrow 원장을 보고 이번에 할 일을 정한다.
     *
     * <ul>
     *   <li>이 환불이 {@code FAILED} 면 멈춘다. 같은 키로 다시 받으면 Escrow 가 송금을 다시 시도하기 때문이다.
     *   <li>처리 중이면 기다리고, {@code COMPLETED} 면 끝났다.
     *   <li>예치가 아직 확정되지 않았으면({@code CREATED}·{@code DEPOSIT_PENDING}) 기다린다. 확정되면 다음 요청에서 돌려준다.
     *   <li>동결됐거나 이미 지급으로 닫힌 에스크로는 사람이 본다.
     *   <li>남은 돈이 없으면 돌려줄 것이 없다. 있으면 같은 멱등키로 전액을 부른다.
     * </ul>
     */
    static RefundOutcome decide(EscrowLedger ledger, String referenceId) {
        List<LedgerEntry> mine = ledger.transactions().stream()
                .filter(entry -> REFUND.equals(entry.type()) && referenceId.equals(entry.referenceId()))
                .toList();
        if (mine.stream().anyMatch(entry -> FAILED.equals(entry.status()))) {
            return RefundOutcome.BLOCKED;
        }
        if (mine.stream().anyMatch(entry -> IN_FLIGHT.contains(entry.status()))) {
            return RefundOutcome.PENDING;
        }
        if (mine.stream().anyMatch(entry -> COMPLETED.equals(entry.status())) || "REFUNDED".equals(ledger.status())) {
            return RefundOutcome.REFUNDED;
        }
        switch (ledger.status()) {
            case "CANCELLED":
                return RefundOutcome.NOTHING_TO_REFUND;
            case "CREATED", "DEPOSIT_PENDING":
                return RefundOutcome.PENDING;
            case ACTIVE:
                break;
            default:
                return RefundOutcome.BLOCKED;
        }
        if (ledger.held()) {
            return RefundOutcome.BLOCKED;
        }
        if (ledger.balance() == null || ledger.balance().compareTo(BigInteger.ZERO) <= 0) {
            return RefundOutcome.NOTHING_TO_REFUND;
        }
        return RefundOutcome.SEND;
    }

    private static void warnIfBlocked(Integer contractId, RefundOutcome outcome) {
        if (outcome == RefundOutcome.BLOCKED) {
            log.warn("refund needs review contractId={}", contractId);
        }
    }

    /** 계약마다 하나뿐인 환불 정산 식별자. Escrow 가 {@code SETTLEMENT:} 로 시작하기를 요구한다. */
    static String referenceIdOf(Integer contractId) {
        return "SETTLEMENT:CANCEL-" + Objects.requireNonNull(contractId);
    }

    /** 계약마다 하나뿐인 환불 멱등키. 바꾸면 같은 환불이 두 번 나갈 수 있으므로 형식을 바꾸지 않는다. */
    static String idempotencyKeyOf(Integer contractId) {
        return "escrow:refund:cancel-contract-" + Objects.requireNonNull(contractId);
    }

    /** 환불 한 번의 결과. */
    public enum RefundOutcome {
        /** 예치가 없거나 남은 돈이 없다. */
        NOTHING_TO_REFUND,
        /** 남은 예치금을 모두 돌려줬다. */
        REFUNDED,
        /** 결과를 모르거나 처리 중이다. 같은 요청을 다시 보내면 같은 멱등키로 잇는다. (`R-PAY-12`) */
        PENDING,
        /** 동결·거절·실패 확정이라 자동으로 잇지 않는다. 사람이 확인한다. */
        BLOCKED,
        /** 이번에 Escrow 를 부른다. {@link #decide} 안에서만 쓴다. */
        SEND
    }
}
