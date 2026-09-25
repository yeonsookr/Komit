package com.a501.service.domain.settlement.service;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 컨설팅 자동 지급을 주기적으로 깨운다.
 *
 * <p><b>일은 여기서 하지 않고 전용 thread 로 넘긴다.</b> {@code @Scheduled} 는 thread 가 하나라 WebSocket
 * heartbeat 와 같이 쓴다. 지급은 Escrow 를 수십 초 기다릴 수 있어, 그 자리에서 돌리면 heartbeat 가 밀려
 * 멀쩡한 연결이 끊긴다. (`R-RT-04`)
 *
 * <p>앞 회차가 아직 돌고 있으면 새 회차를 쌓지 않고 넘긴다. 모든 서버가 깨어나지만 같은 계약은 {@link PayoutLock}
 * 이 한 곳에만 준다.
 */
@Slf4j
@Component
public class ConsultingPayoutScheduler implements DisposableBean {
    private final ConsultingPayoutService payoutService;
    private final ThreadPoolTaskExecutor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ConsultingPayoutScheduler(ConsultingPayoutService payoutService) {
        this.payoutService = payoutService;
        this.executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("consulting-payout-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.initialize();
    }

    @Scheduled(
            initialDelayString = "${app.settlement.payout-initial-delay}",
            fixedDelayString = "${app.settlement.payout-interval}")
    void trigger() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::run);
        } catch (RuntimeException rejected) {
            running.set(false);
            log.warn("consulting payout not started error={}", rejected.getClass().getSimpleName());
        }
    }

    private void run() {
        try {
            payoutService.payDue();
        } catch (RuntimeException exception) {
            log.warn("consulting payout round failed error={}", exception.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
