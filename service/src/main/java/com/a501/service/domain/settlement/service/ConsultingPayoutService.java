package com.a501.service.domain.settlement.service;

import com.a501.service.domain.account.dto.PayoutAccount;
import com.a501.service.domain.account.service.AccountQueryService;
import com.a501.service.domain.consulting.dto.SessionSchedule;
import com.a501.service.domain.consulting.service.ConsultingSessionQueryService;
import com.a501.service.domain.contract.dto.ConsultingPayoutTarget;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.contract.service.ContractSettlementService;
import com.a501.service.domain.report.service.ReportQueryService;
import com.a501.service.domain.settlement.client.EscrowPayoutClient;
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
        return EscrowRelease.pay(
                escrow,
                new EscrowRelease.Request(
                        target.contractId(),
                        referenceIdOf(target.contractId()),
                        idempotencyKeyOf(target.contractId()),
                        target.amount()),
                account,
                () -> contractSettlementService.completeConsultingPayout(target.consultingAgreementId()));
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
