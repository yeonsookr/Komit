package com.a501.service.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.config.StorageProperties;
import com.a501.service.domain.file.entity.StoredFile;
import com.a501.service.domain.file.repository.StoredFileRepository;
import io.minio.MinioClient;
import java.time.Duration;
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
    private MinioClient minioClient;

    @Test
    void findReadUrlsIssuesAnUrlOnlyForCompletedFiles() throws Exception {
        StoredFile completed = file(512L, "COMPLETED", "portfolio", "12/512.png");
        StoredFile pending = file(513L, "PENDING", "portfolio", "12/513.png");
        when(storedFileRepository.findByFileIdInAndDeletedFalse(any()))
                .thenReturn(List.of(completed, pending));
        when(minioClient.getPresignedObjectUrl(any()))
                .thenReturn("http://localhost:9000/portfolio/12/512.png?signature=test");
        FileLookupService service = new FileLookupService(
                storedFileRepository,
                minioClient,
                new StorageProperties("http://localhost:9000", "access", "secret", Duration.ofMinutes(15)));

        var urls = service.findReadUrls(List.of(512, 513));

        assertThat(urls).containsExactlyEntriesOf(
                java.util.Map.of(512, "http://localhost:9000/portfolio/12/512.png?signature=test"));
        verify(minioClient).getPresignedObjectUrl(any());
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
