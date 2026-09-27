package com.a501.service.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.file.entity.StoredFile;
import com.a501.service.domain.file.repository.StoredFileRepository;
import java.util.List;
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
    void bigintFileIdIsNotTruncatedWhenFindingNamesAndReadUrls() {
        long largeId = (long) Integer.MAX_VALUE + 1;
        StoredFile completed = file(largeId, "COMPLETED", "deliverables", "files/large");
        ReflectionTestUtils.setField(completed, "originalName", "large.zip");
        when(storedFileRepository.findByFileIdInAndDeletedFalse(any()))
                .thenReturn(List.of(completed));
        when(objectStorageService.issueReadUrl(completed)).thenReturn("https://storage.example/large");
        FileLookupService service = new FileLookupService(storedFileRepository, objectStorageService);

        assertThat(service.findOriginalNamesLong(List.of(largeId))).containsEntry(largeId, "large.zip");
        assertThat(service.findReadUrlsLong(List.of(largeId)))
                .containsEntry(largeId, "https://storage.example/large");
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
    void bigintReadUrlIsIssuedOnlyForCompletedFiles() {
        long largeId = (long) Integer.MAX_VALUE + 1;
        StoredFile pending = file(largeId, "PENDING", "deliverables", "files/pending");
        when(storedFileRepository.findByFileIdInAndDeletedFalse(any())).thenReturn(List.of(pending));
        FileLookupService service = new FileLookupService(storedFileRepository, objectStorageService);

        assertThat(service.findReadUrlsLong(List.of(largeId))).isEmpty();
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
