package com.a501.service.domain.contract.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.domain.contract.repository.MilestoneRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 자동 통과 회차가 기한이 지난 작업을 모두 보고, 하나가 실패해도 나머지를 계속하는지 본다. (R-QA-02) */
class MilestoneAutoPassServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 27, 10, 0);

    private final MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
    private final MilestoneProgressService progressService = mock(MilestoneProgressService.class);
    private final MilestoneAutoPassService service = new MilestoneAutoPassService(milestoneRepository, progressService);

    @Test
    @DisplayName("3일 전까지 요청한 작업을 찾아 하나씩 통과시키고, 실패한 작업이 있어도 다음을 본다")
    void passesEveryDueWorkEvenIfOneFails() {
        when(milestoneRepository.findSubmittedIdsBefore(NOW.minusDays(3))).thenReturn(List.of(301, 302, 303));
        when(progressService.autoPass(301, NOW)).thenReturn(true);
        when(progressService.autoPass(302, NOW)).thenThrow(new IllegalStateException("lock timeout"));
        when(progressService.autoPass(303, NOW)).thenReturn(false);

        service.passDue(NOW);

        verify(progressService).autoPass(301, NOW);
        verify(progressService).autoPass(302, NOW);
        verify(progressService).autoPass(303, NOW);
    }
}
