package com.a501.service.domain.settlement.service;

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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 컨설팅 대금을 이의 제기 기간이 지나면 개발자에게 보낸다. (`R-CON-09`~`R-CON-12`)
 *
 * <p>사용자가 누르는 버튼은 없다. {@link ConsultingPayoutScheduler} 가 주기적으로 {@link #payDue()} 를
 * 부르고, 조건을 다 갖춘 계약만 Escrow 에 지급을 부탁한다.
 *
 * <p><b>이 서버는 지급 상태를 저장하지 않는다.</b> 멱등키와 정산 식별자를 계약 id 로 정해 두고, 무엇을
 * 할지는 매번 Escrow 의 거래 목록을 읽어 정한다. 지급이 확정되면 계약 쪽에 완료만 적어 다음 회차에서
 * 빠지게 한다. (`R-PAY-05`)
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> Escrow 를 부르는 동안 DB 연결을 붙잡지 않기 위해서다. 조회와 완료
 * 기록은 각 도메인 서비스가 자기 트랜잭션으로 짧게 끝낸다. (BACKEND_CONVENTIONS 16장)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultingPayoutService {
    /** R-CON-10: 예약된 세션 종료 뒤 의뢰인이 이의를 낼 수 있는 기간. 지나면 자동 지급한다. */
    static final Duration OBJECTION_PERIOD = Duration.ofDays(3);

    private static final String RELEASE = "RELEASE";
    private static final String FEE = "FEE";
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";
    private static final String ACTIVE = "ACTIVE";
    private static final List<String> IN_FLIGHT = List.of("PENDING", "PROCESSING");

    private final ContractQueryService contractQueryService;
    private final ContractSettlementService contractSettlementService;
    private final ConsultingSessionQueryService sessionQueryService;
    private final ReportQueryService reportQueryService;
    private final AccountQueryService accountQueryService;
    private final EscrowPayoutClient escrow;
    private final PayoutLock lock;

    /**
     * 지급을 마치지 않은 컨설팅 계약을 하나씩 보고, 때가 된 것을 지급한다.
     *
     * <p>한 계약에서 실패해도 나머지는 계속한다. 실패한 계약은 다음 회차에 같은 멱등키로 다시 본다.
     */
    public void payDue() {
        payDue(LocalDateTime.now(ZoneOffset.UTC));
    }

    /** {@link #payDue()} 를 정해진 시각으로 돌린다. 테스트가 시각을 고정할 때 쓴다. */
    void payDue(LocalDateTime now) {
        for (ConsultingPayoutTarget target : contractQueryService.findUnpaidConsultingContracts()) {
            try {
                PayoutOutcome outcome = payIfDue(target, now);
                if (outcome.isNoteworthy()) {
                    log.info("consulting payout contractId={} outcome={}", target.contractId(), outcome);
                }
            } catch (RuntimeException exception) {
                log.warn("consulting payout aborted contractId={} error={}",
                        target.contractId(), exception.getClass().getSimpleName());
            }
        }
    }

    /**
     * 계약 한 건을 지급할 때가 됐는지 보고, 됐으면 지급한다.
     *
     * @param now 지금(UTC)
     * @return 이번 회차에 일어난 일
     */
    PayoutOutcome payIfDue(ConsultingPayoutTarget target, LocalDateTime now) {
        List<SessionSchedule> schedules = sessionQueryService.findSchedules(target.consultingAgreementId());
        if (schedules.isEmpty() || schedules.stream().anyMatch(schedule -> schedule.scheduledEnd() == null)) {
            return PayoutOutcome.NO_SCHEDULE;
        }
        LocalDateTime lastEnd = schedules.stream()
                .map(SessionSchedule::scheduledEnd)
                .max(LocalDateTime::compareTo)
                .orElseThrow();
        if (now.isBefore(lastEnd.plus(OBJECTION_PERIOD))) {
            return PayoutOutcome.NOT_DUE;
        }
        // R-CON-12: 정상 진행·종료를 확인할 수 없는 세션은 자동 지급하지 않고 보류한다.
        if (!schedules.stream().allMatch(SessionSchedule::ranToEnd)) {
            return PayoutOutcome.SESSION_EXCEPTION;
        }
        // R-CON-11, R-QA-02: 해결되지 않은 신고가 있으면 지급하지 않는다.
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
            return pay(target, account.get());
        } finally {
            lock.release(target.contractId(), token.get());
        }
    }

    private PayoutOutcome pay(ConsultingPayoutTarget target, PayoutAccount account) {
        String referenceId = referenceIdOf(target.contractId());
        try {
            Optional<EscrowLedger> ledger = escrow.findByContract(String.valueOf(target.contractId()));
            if (ledger.isEmpty()) {
                return PayoutOutcome.NOT_DEPOSITED;
            }
            BigInteger amount = BigInteger.valueOf(target.amount());
            PayoutOutcome decision = decide(ledger.get(), referenceId, amount);
            if (decision == PayoutOutcome.PAID) {
                contractSettlementService.completeConsultingPayout(target.consultingAgreementId());
                return PayoutOutcome.PAID;
            }
            if (decision != PayoutOutcome.SENT) {
                return decision;
            }

            String status = escrow.release(new PayoutRequest(
                    ledger.get().escrowId(),
                    referenceId,
                    amount,
                    account.bankCode(),
                    account.accountNo(),
                    account.holderName(),
                    idempotencyKeyOf(target.contractId())));
            if (COMPLETED.equals(status)) {
                contractSettlementService.completeConsultingPayout(target.consultingAgreementId());
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

    /** 계약마다 하나뿐인 정산 식별자. Escrow 가 {@code SETTLEMENT:} 로 시작하기를 요구한다. */
    static String referenceIdOf(Integer contractId) {
        return "SETTLEMENT:CONSULTING-" + Objects.requireNonNull(contractId);
    }

    /** 계약마다 하나뿐인 지급 멱등키. 바꾸면 같은 지급이 두 번 나갈 수 있으므로 형식을 바꾸지 않는다. */
    static String idempotencyKeyOf(Integer contractId) {
        return "escrow:release:consulting-contract-" + Objects.requireNonNull(contractId);
    }
}
