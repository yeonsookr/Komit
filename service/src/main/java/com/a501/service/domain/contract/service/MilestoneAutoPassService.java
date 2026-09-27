package com.a501.service.domain.contract.service;

import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 확인을 요청한 뒤 기한이 지나도록 의뢰인이 답하지 않은 작업을 자동으로 통과시킨다. (R-QA-02)
 *
 * <p>사용자가 누르는 버튼은 없다. {@link MilestoneAutoPassScheduler} 가 주기적으로 {@link #passDue()} 를 부른다.
 * 작업 하나마다 따로 트랜잭션을 열어, 한 작업이 실패하거나 잠금을 기다려도 나머지는 계속한다. 실패한 작업은 다음
 * 회차에 다시 본다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilestoneAutoPassService {
    private final MilestoneRepository milestoneRepository;
    private final MilestoneProgressService progressService;

    public void passDue() {
        passDue(LocalDateTime.now(ZoneOffset.UTC));
    }

    /** {@link #passDue()} 를 정해진 시각으로 돌린다. 테스트가 시각을 고정할 때 쓴다. */
    void passDue(LocalDateTime now) {
        for (Integer milestoneId : milestoneRepository.findSubmittedIdsBefore(now.minusDays(Milestone.AUTO_PASS_DAYS))) {
            try {
                if (progressService.autoPass(milestoneId, now)) {
                    log.info("milestone auto passed milestoneId={}", milestoneId);
                }
            } catch (RuntimeException exception) {
                log.warn("milestone auto pass aborted milestoneId={} error={}",
                        milestoneId, exception.getClass().getSimpleName());
            }
        }
    }
}
