package com.a501.service.domain.developer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.developer.dto.request.WritePortfolioItemRequest;
import com.a501.service.domain.developer.entity.PortfolioItem;
import com.a501.service.domain.developer.repository.PortfolioItemRepository;
import com.a501.service.domain.file.service.FileLookupService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PortfolioItemServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 3, 0);

    @Mock
    private PortfolioItemRepository portfolioItemRepository;
    @Mock
    private FileLookupService fileLookupService;
    @InjectMocks
    private PortfolioItemService service;

    private static PortfolioItem existing(Integer id, String title) {
        PortfolioItem item = PortfolioItem.create(3, title, null, null, null);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    @Test
    void replaceAllCreatesUpdatesAndSoftDeletesByTheRequestList() {
        PortfolioItem kept = existing(7, "옛 제목");
        PortfolioItem dropped = existing(8, "빠진 줄");
        when(portfolioItemRepository.findByDeveloperIdAndDeletedFalseOrderByIdAsc(3)).thenReturn(List.of(kept, dropped));

        service.replaceAll(3, 12, List.of(
                new WritePortfolioItemRequest(7, "새 제목", "설명", "https://a.example", 512),
                new WritePortfolioItemRequest(null, "새 줄", null, null, null)), NOW);

        assertThat(kept.getTitle()).isEqualTo("새 제목");
        assertThat(kept.getThumbnailId()).isEqualTo(512);
        assertThat(dropped.isDeleted()).isTrue();
        assertThat(dropped.getDeletedAt()).isEqualTo(NOW);
        assertThat(dropped.getRecordStatus()).isEqualTo("DELETED");
        ArgumentCaptor<PortfolioItem> saved = ArgumentCaptor.forClass(PortfolioItem.class);
        verify(portfolioItemRepository).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("새 줄");
        assertThat(saved.getValue().getDeveloperId()).isEqualTo(3);
        verify(fileLookupService).assertAttachable(eq(12), eq(List.of(512)));
    }

    @Test
    void foreignOrDeletedIdIsForbiddenAndNothingIsSaved() {
        when(portfolioItemRepository.findByDeveloperIdAndDeletedFalseOrderByIdAsc(3)).thenReturn(List.of(existing(7, "내 것")));

        assertThatThrownBy(() -> service.replaceAll(3, 12,
                List.of(new WritePortfolioItemRequest(99, "남의 것", null, null, null)), NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);

        verify(portfolioItemRepository, never()).save(any());
        verify(fileLookupService, never()).assertAttachable(any(), any());
    }

    @Test
    void listMineKeepsRegistrationOrderAndLeavesThumbnailUrlNullUntilFileApi() {
        when(portfolioItemRepository.findByDeveloperIdAndDeletedFalseOrderByIdAsc(3))
                .thenReturn(List.of(existing(7, "첫째"), existing(9, "둘째")));

        var items = service.listMine(3);

        assertThat(items).extracting("portfolioItemId").containsExactly(7, 9);
        assertThat(items).extracting("thumbnailUrl").containsOnlyNulls();
    }
}
