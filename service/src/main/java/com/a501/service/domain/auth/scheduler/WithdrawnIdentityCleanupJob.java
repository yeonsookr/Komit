package com.a501.service.domain.auth.scheduler;

import com.a501.service.domain.auth.service.WithdrawnIdentityService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보관 기간이 지난 탈퇴 회원의 식별 정보를 지우는 정기 작업. (R-ACC-06)
 *
 * <p>모든 파드에서 돕니다. 사람마다 사용자 행을 잠근 뒤 다시 보고 지우므로 여러 파드가 같은 사람을 골라도 한 번만 바뀌고,
 * 지운 사람은 목록에서 빠집니다. (BACKEND_CONVENTIONS 6.6) 한 사람이 실패하면 이번 차례를 멈추고 다음 차례에 이어갑니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawnIdentityCleanupJob {
    /** 한 번에 가져올 사람 수. 한 차례가 사용자 표를 오래 훑지 않게 나눕니다. */
    static final int BATCH_SIZE = 100;

    private final WithdrawnIdentityService withdrawnIdentityService;

    @Scheduled(cron = "${app.withdrawal.anonymize-cron}", zone = "Asia/Seoul")
    public void run() {
        int total = 0;
        while (true) {
            List<Integer> targets = withdrawnIdentityService.findExpired(BATCH_SIZE);
            int done = (int) targets.stream().filter(withdrawnIdentityService::anonymize).count();
            total += done;
            // 다른 파드가 먼저 지워 이번 묶음에서 바꾼 것이 없으면, 남은 것도 그쪽이 맡고 있으므로 여기서 멈춥니다.
            if (targets.size() < BATCH_SIZE || done == 0) {
                break;
            }
        }
        if (total > 0) {
            log.info("탈퇴 회원 식별 정보 정리. count={}", total);
        }
    }
}
