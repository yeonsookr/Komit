package com.a501.service.domain.settlement.service;

import com.a501.service.domain.account.dto.PayoutAccount;
import com.a501.service.domain.account.service.AccountQueryService;
import com.a501.service.domain.contract.dto.DevelopmentPayoutTarget;
import com.a501.service.domain.contract.service.ContractSettlementService;
import com.a501.service.domain.contract.service.DevelopmentPayoutQueryService;
import com.a501.service.domain.report.service.ReportQueryService;
import com.a501.service.domain.settlement.client.EscrowPayoutClient;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 최종 인수 시험을 통과한 개발 계약의 대금 전액을 개발자에게 보낸다. (`R-PAY-01`, `R-QA-06`)
 *
 * <p>사용자가 누르는 버튼은 없다. {@link DevelopmentPayoutScheduler} 가 주기적으로 {@link #payDue()} 를 부른다.
 * 계약 총액을 한 번만 부탁한다. 작업별 금액은 총액을 나눠 보여 주는 값일 뿐 따로 지급하지 않는다.
 *
 * <p>컨설팅 지급과 같이 지급 상태를 저장하지 않는다. 정산 식별자와 멱등키를 계약 id 로 정해 두고, 무엇을 할지는 매번
 * Escrow 의 거래 목록을 읽어 정한다. 지급액과 수수료 이체가 모두 확정되면 개발 계약을 완료로 적는다. 그 전에는 리뷰와
 * 지급 뒤 해금 파일이 열리지 않는다. (`R-PAY-05`, `R-PAY-07`, `R-PAY-12`)
 *
 * <p>트랜잭션을 걸지 않는다. Escrow 를 부르는 동안 DB 연결을 붙잡지 않기 위해서다. (BACKEND_CONVENTIONS 16장)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevelopmentPayoutService {
    private final DevelopmentPayoutQueryService payoutQueryService;
    private final ContractSettlementService contractSettlementService;
    private final ReportQueryService reportQueryService;
    private final AccountQueryService accountQueryService;
    private final EscrowPayoutClient escrow;
    private final PayoutLock lock;

    /**
     * 최종 인수 시험을 통과하고 지급을 마치지 않은 개발 계약을 하나씩 본다.
     *
     * <p>한 계약에서 실패해도 나머지는 계속한다. 실패한 계약은 다음 회차에 같은 멱등키로 다시 본다.
     */
    public void payDue() {
        for (DevelopmentPayoutTarget target : payoutQueryService.findUnpaidDevelopmentContracts()) {
            try {
                PayoutOutcome outcome = payIfAllowed(target);
                if (outcome.isNoteworthy()) {
                    log.info("development payout contractId={} outcome={}", target.contractId(), outcome);
                }
            } catch (RuntimeException exception) {
                log.warn("development payout aborted contractId={} error={}",
                        target.contractId(), exception.getClass().getSimpleName());
            }
        }
    }

    /**
     * 계약 한 건을 지급해도 되는지 보고, 되면 지급한다.
     *
     * @return 이번 회차에 일어난 일
     */
    PayoutOutcome payIfAllowed(DevelopmentPayoutTarget target) {
        // R-QA-02: 해결되지 않은 신고가 있으면 지급 절차를 진행하지 않는다. 자금 동결도 신고로 생긴다(R-RPT-05).
        if (reportQueryService.hasOpenReport(target.contractId())) {
            return PayoutOutcome.REPORTED;
        }
        if (target.amount() <= 0) {
            return PayoutOutcome.NO_AMOUNT;
        }
        // R-PAY-14: 받을 계좌가 없으면 등록할 때까지 기다린다.
        Optional<PayoutAccount> account = accountQueryService.findPayoutAccount(target.developerId());
        if (account.isEmpty()) {
            return PayoutOutcome.NO_ACCOUNT;
        }

        Optional<String> token = lock.tryAcquire(target.contractId());
        if (token.isEmpty()) {
            return PayoutOutcome.BUSY;
        }
        try {
            return EscrowRelease.pay(
                    escrow,
                    new EscrowRelease.Request(
                            target.contractId(),
                            referenceIdOf(target.contractId()),
                            idempotencyKeyOf(target.contractId()),
                            target.amount()),
                    account.get(),
                    () -> contractSettlementService.completeDevelopmentPayout(target.developmentContractId()));
        } finally {
            lock.release(target.contractId(), token.get());
        }
    }

    /** 계약마다 하나뿐인 정산 식별자. 컨설팅과 겹치지 않게 갈래를 붙인다. Escrow 가 {@code SETTLEMENT:} 로 시작하기를 요구한다. */
    static String referenceIdOf(Integer contractId) {
        return "SETTLEMENT:DEVELOPMENT-" + Objects.requireNonNull(contractId);
    }

    /** 계약마다 하나뿐인 지급 멱등키. 바꾸면 같은 지급이 두 번 나갈 수 있으므로 형식을 바꾸지 않는다. */
    static String idempotencyKeyOf(Integer contractId) {
        return "escrow:release:development-contract-" + Objects.requireNonNull(contractId);
    }
}
