package com.a501.service.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.file.FileErrorCode;
import com.a501.service.domain.file.dto.request.UploadPurpose;
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
    void 그_용도로_올린_내_완료된_파일만_붙일_수_있다() {
        givenFile(512L, "COMPLETED", 12, UploadPurpose.SESSION_RECORDING.name());
        FileLookupService service = new FileLookupService(storedFileRepository, objectStorageService);

        assertThatCode(() -> service.assertAttachable(12, 512, UploadPurpose.SESSION_RECORDING))
                .doesNotThrowAnyException();
    }

    /** 다른 데 쓰려고 올린 파일이 엉뚱한 자리에 붙으면 전사가 읽지 못합니다. */
    @Test
    void 다른_용도로_올린_파일은_거절한다() {
        givenFile(512L, "COMPLETED", 12, UploadPurpose.PROFILE_IMAGE.name());
        FileLookupService service = new FileLookupService(storedFileRepository, objectStorageService);

        assertThatThrownBy(() -> service.assertAttachable(12, 512, UploadPurpose.SESSION_RECORDING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", FileErrorCode.FILE_NOT_ATTACHABLE);
    }

    /** 용도 칸이 생기기 전에 올린 파일이라 무엇으로 올렸는지 알 수 없습니다. */
    @Test
    void 용도가_비어_있는_파일은_거절한다() {
        givenFile(512L, "COMPLETED", 12, null);
        FileLookupService service = new FileLookupService(storedFileRepository, objectStorageService);

        assertThatThrownBy(() -> service.assertAttachable(12, 512, UploadPurpose.SESSION_RECORDING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", FileErrorCode.FILE_NOT_ATTACHABLE);
    }

    @Test
    void 남의_파일과_완료_전_파일은_거절한다() {
        givenFile(512L, "COMPLETED", 99, UploadPurpose.SESSION_RECORDING.name());
        FileLookupService service = new FileLookupService(storedFileRepository, objectStorageService);

        assertThatThrownBy(() -> service.assertAttachable(12, 512, UploadPurpose.SESSION_RECORDING))
                .isInstanceOf(BusinessException.class);

        givenFile(513L, "PENDING", 12, UploadPurpose.SESSION_RECORDING.name());
        assertThatThrownBy(() -> service.assertAttachable(12, 513, UploadPurpose.SESSION_RECORDING))
                .isInstanceOf(BusinessException.class);
    }

    private void givenFile(Long id, String status, Integer userId, String purpose) {
        StoredFile file = BeanUtils.instantiateClass(StoredFile.class);
        ReflectionTestUtils.setField(file, "fileId", id);
        ReflectionTestUtils.setField(file, "status", status);
        ReflectionTestUtils.setField(file, "userId", userId);
        ReflectionTestUtils.setField(file, "purpose", purpose);
        when(storedFileRepository.findByFileIdAndDeletedFalse(id))
                .thenReturn(java.util.Optional.of(file));
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
