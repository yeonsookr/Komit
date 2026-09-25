package com.a501.service.domain.auth.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.domain.auth.service.WithdrawnIdentityService;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawnIdentityCleanupJobTest {
    @Mock
    private WithdrawnIdentityService withdrawnIdentityService;
    @InjectMocks
    private WithdrawnIdentityCleanupJob job;

    @Test
    @DisplayName("한 묶음이 가득 차면 다음 묶음을 더 가져오고, 덜 차면 멈춘다")
    void pagesUntilBatchIsNotFull() {
        List<Integer> full = IntStream.rangeClosed(1, WithdrawnIdentityCleanupJob.BATCH_SIZE).boxed().toList();
        when(withdrawnIdentityService.findExpired(WithdrawnIdentityCleanupJob.BATCH_SIZE))
                .thenReturn(full, List.of(101));
        when(withdrawnIdentityService.anonymize(any())).thenReturn(true);

        job.run();

        verify(withdrawnIdentityService, times(2)).findExpired(WithdrawnIdentityCleanupJob.BATCH_SIZE);
        verify(withdrawnIdentityService).anonymize(101);
    }

    @Test
    @DisplayName("가득 찬 묶음에서 바꾼 것이 없으면 다른 파드가 맡은 것으로 보고 멈춘다")
    void stopsWhenAnotherPodTookTheBatch() {
        List<Integer> full = IntStream.rangeClosed(1, WithdrawnIdentityCleanupJob.BATCH_SIZE).boxed().toList();
        when(withdrawnIdentityService.findExpired(WithdrawnIdentityCleanupJob.BATCH_SIZE)).thenReturn(full);
        when(withdrawnIdentityService.anonymize(any())).thenReturn(false);

        job.run();

        verify(withdrawnIdentityService, times(1)).findExpired(WithdrawnIdentityCleanupJob.BATCH_SIZE);
    }

    @Test
    @DisplayName("지울 사람이 없으면 아무것도 하지 않는다")
    void nothingToDo() {
        when(withdrawnIdentityService.findExpired(WithdrawnIdentityCleanupJob.BATCH_SIZE)).thenReturn(List.of());

        job.run();

        verify(withdrawnIdentityService, never()).anonymize(any());
    }
}
