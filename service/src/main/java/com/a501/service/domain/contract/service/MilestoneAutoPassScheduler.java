package com.a501.service.domain.contract.service;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 작업 자동 통과를 주기적으로 깨운다. (R-QA-02)
 *
 * <p><b>일은 여기서 하지 않고 전용 thread 로 넘긴다.</b> {@code @Scheduled} 는 thread 가 하나라 WebSocket
 * heartbeat 와 같이 쓴다. 자동 통과는 계약 행 잠금을 기다릴 수 있어, 그 자리에서 돌리면 heartbeat 가 밀린다.
 * (`R-RT-04`)
 *
 * <p>앞 회차가 아직 돌고 있으면 새 회차를 쌓지 않고 넘긴다. 모든 서버가 깨어나지만 같은 작업은 계약 행 잠금과 상태
 * 재확인으로 한 번만 통과한다.
 */
@Slf4j
@Component
public class MilestoneAutoPassScheduler implements DisposableBean {
    private final MilestoneAutoPassService autoPassService;
    private final ThreadPoolTaskExecutor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MilestoneAutoPassScheduler(MilestoneAutoPassService autoPassService) {
        this.autoPassService = autoPassService;
        this.executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("milestone-auto-pass-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.initialize();
    }

    @Scheduled(
            initialDelayString = "${app.contract.auto-pass-initial-delay}",
            fixedDelayString = "${app.contract.auto-pass-interval}")
    void trigger() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::run);
        } catch (RuntimeException rejected) {
            running.set(false);
            log.warn("milestone auto pass not started error={}", rejected.getClass().getSimpleName());
        }
    }

    private void run() {
        try {
            autoPassService.passDue();
        } catch (RuntimeException exception) {
            log.warn("milestone auto pass round failed error={}", exception.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
