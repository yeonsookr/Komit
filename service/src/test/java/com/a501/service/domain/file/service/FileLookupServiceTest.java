package com.a501.service.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.file.entity.StoredFile;
import com.a501.service.domain.file.repository.StoredFileRepository;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FileLookupServiceTest {
    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private ObjectStorageService objectStorageService;

    @Test
    void findReadUrlsIssuesAnUrlOnlyForCompletedFiles() throws Exception {
        StoredFile completed = file(512L, "COMPLETED", "portfolio", "12/512.png");
        StoredFile pending = file(513L, "PENDING", "portfolio", "12/513.png");
        when(storedFileRepository.findByFileIdInAndDeletedFalse(any()))
                .thenReturn(List.of(completed, pending));
        when(objectStorageService.issueReadUrl(completed))
                .thenReturn("http://localhost:9000/portfolio/12/512.png?signature=test");
        FileLookupService service = new FileLookupService(storedFileRepository, objectStorageService);

        var urls = service.findReadUrls(List.of(512, 513));

        assertThat(urls).containsExactlyEntriesOf(
                java.util.Map.of(512, "http://localhost:9000/portfolio/12/512.png?signature=test"));
        verify(objectStorageService).issueReadUrl(completed);
    }

    @Test
    void emptyLookupsTolerateNullKeysSoCallersCanLookUpItemsWithoutFiles() {
        FileLookupService service = new FileLookupService(storedFileRepository, objectStorageService);

        assertThat(service.findReadUrls(List.of()).get(null)).isNull();
        assertThat(service.findOriginalNames(List.of()).get(null)).isNull();
    }

    @Test
    void bigintFileIdIsNotTruncatedWhenOpeningAFile() {
        long largeId = (long) Integer.MAX_VALUE + 1;
        StoredFile completed = file(largeId, "COMPLETED", "deliverables", "files/large");
        ReflectionTestUtils.setField(completed, "originalName", "인계 소스.zip");
        ReflectionTestUtils.setField(completed, "sizeBytes", 3L);
        InputStream body = new ByteArrayInputStream(new byte[] {1, 2, 3});
        when(storedFileRepository.findByFileIdInAndDeletedFalse(Set.of(largeId))).thenReturn(List.of(completed));
        when(objectStorageService.open(completed)).thenReturn(body);
        FileLookupService service = new FileLookupService(storedFileRepository, objectStorageService);

        FileLookupService.FileContent content = service.open(largeId).orElseThrow();

        assertThat(content.originalName()).isEqualTo("인계 소스.zip");
        assertThat(content.sizeBytes()).isEqualTo(3L);
        assertThat(content.body()).isSameAs(body);
    }

    @Test
    void bigintFileMustBeCompletedAndOwnedByAttachingUser() {
        long largeId = (long) Integer.MAX_VALUE + 1;
        StoredFile pending = file(largeId, "PENDING", "deliverables", "files/pending");
        ReflectionTestUtils.setField(pending, "userId", 12);
        when(storedFileRepository.findByFileIdInAndDeletedFalse(any())).thenReturn(List.of(pending));
        FileLookupService service = new FileLookupService(storedFileRepository, objectStorageService);

        assertThatThrownBy(() -> service.assertAttachableLong(12, List.of(largeId)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void bigintFileOfAnotherUserIsNotAttachableButOwnCompletedFileIs() {
        long largeId = (long) Integer.MAX_VALUE + 1;
        StoredFile completed = file(largeId, "COMPLETED", "deliverables", "files/large");
        ReflectionTestUtils.setField(completed, "userId", 12);
        when(storedFileRepository.findByFileIdInAndDeletedFalse(any())).thenReturn(List.of(completed));
        FileLookupService service = new FileLookupService(storedFileRepository, objectStorageService);

        service.assertAttachableLong(12, List.of(largeId));
        assertThatThrownBy(() -> service.assertAttachableLong(13, List.of(largeId)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void opensOnlyCompletedFilesAndNothingForNoFile() {
        long largeId = (long) Integer.MAX_VALUE + 1;
        StoredFile pending = file(largeId, "PENDING", "deliverables", "files/pending");
        when(storedFileRepository.findByFileIdInAndDeletedFalse(any())).thenReturn(List.of(pending));
        FileLookupService service = new FileLookupService(storedFileRepository, objectStorageService);

        assertThat(service.open(largeId)).isEmpty();
        assertThat(service.open(null)).isEmpty();
        verify(objectStorageService, never()).open(any());
    }

    private static StoredFile file(Long id, String status, String bucketName, String storageKey) {
        StoredFile file = BeanUtils.instantiateClass(StoredFile.class);
        ReflectionTestUtils.setField(file, "fileId", id);
        ReflectionTestUtils.setField(file, "status", status);
        ReflectionTestUtils.setField(file, "bucketName", bucketName);
        ReflectionTestUtils.setField(file, "storageKey", storageKey);
        return file;
    }
}
