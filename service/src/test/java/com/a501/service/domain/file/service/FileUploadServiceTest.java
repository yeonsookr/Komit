package com.a501.service.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.config.StorageProperties;
import com.a501.service.domain.file.FileErrorCode;
import com.a501.service.domain.file.dto.request.CreateUploadRequest;
import com.a501.service.domain.file.dto.request.UploadPurpose;
import com.a501.service.domain.file.entity.StoredFile;
import com.a501.service.domain.file.service.ObjectStorageService.StoredObjectMetadata;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-21T03:00:00Z");
    private static final StorageProperties PROPERTIES = new StorageProperties(
            "http://minio:9000",
            "http://localhost:9000",
            "access",
            "secret",
            "komit",
            "us-east-1",
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            30 * 1024 * 1024L);

    @Mock
    private FileUploadStateService stateService;
    @Mock
    private ObjectStorageService objectStorageService;
    private FileUploadService service;

    @BeforeEach
    void setUp() {
        service = new FileUploadService(
                stateService,
                objectStorageService,
                PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void startRejectsFilesOverTheConfiguredLimitBeforeCreatingMetadata() {
        CreateUploadRequest request = new CreateUploadRequest(
                "large.png", "image/png", PROPERTIES.maxFileBytes() + 1, UploadPurpose.PROFILE_IMAGE);

        assertThatThrownBy(() -> service.start(12, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode().code())
                        .isEqualTo("FILE_TOO_LARGE"));
        verify(stateService, never()).createPending(any(), any(), any(), any(), any());
    }

    @Test
    void startRejectsATypeNotAllowedForThePurpose() {
        CreateUploadRequest request = new CreateUploadRequest(
                "signature.jpg", "image/jpeg", 10L, UploadPurpose.SIGNATURE);

        assertThatThrownBy(() -> service.start(12, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(FileErrorCode.UNSUPPORTED_FILE_TYPE));
    }

    @Test
    void startReturnsThePresignedPutContract() {
        StoredFile file = pending(512L, NOW);
        when(stateService.createPending(eq(12), eq("portfolio.png"), eq("image/png"), eq(4L), any())).thenReturn(file);
        when(objectStorageService.issueUploadUrl(file)).thenReturn("http://localhost:9000/put");

        var result = service.start(12, new CreateUploadRequest(
                "portfolio.png", "image/png", 4L, UploadPurpose.PORTFOLIO_IMAGE));

        assertThat(result.uploadId()).isEqualTo(512);
        assertThat(result.uploadUrl()).isEqualTo("http://localhost:9000/put");
        assertThat(result.headers()).containsExactly(Map.entry("Content-Type", "image/png"));
        assertThat(result.expiresAt()).hasToString("2026-09-21T12:05+09:00");
    }

    @Test
    void completeLeavesAMissingObjectPendingSoTheClientCanRetry() {
        StoredFile file = pending(512L, NOW.minusSeconds(30));
        when(stateService.getOwned(12, 512)).thenReturn(file);
        when(objectStorageService.inspect(file)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(12, 512))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(FileErrorCode.UPLOAD_NOT_COMPLETED));
        verify(stateService, never()).fail(any(), any());
    }

    @Test
    void completeDeletesAndFailsAFileWhoseSizeDiffers() {
        StoredFile file = pending(512L, NOW.minusSeconds(30));
        when(stateService.getOwned(12, 512)).thenReturn(file);
        when(objectStorageService.inspect(file)).thenReturn(Optional.of(new StoredObjectMetadata(3, "image/png")));

        assertThatThrownBy(() -> service.complete(12, 512))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(FileErrorCode.UPLOAD_SIZE_MISMATCH));
        verify(objectStorageService).deleteSilently(file);
        verify(stateService).fail(12, 512);
    }

    @Test
    void completePromotesAValidatedObjectAndReturnsTheCompletedFile() {
        StoredFile pending = pending(512L, NOW.minusSeconds(30));
        StoredFile completed = pending(512L, NOW.minusSeconds(30));
        completed.complete("files/portfolio-image/12/test", NOW);
        when(stateService.getOwned(12, 512)).thenReturn(pending);
        when(objectStorageService.inspect(pending)).thenReturn(Optional.of(new StoredObjectMetadata(4, "image/png")));
        when(objectStorageService.promote(pending)).thenReturn("files/portfolio-image/12/test");
        when(stateService.complete(12, 512, "files/portfolio-image/12/test", NOW)).thenReturn(completed);

        var result = service.complete(12, 512);

        assertThat(result.fileId()).isEqualTo(512);
        assertThat(result.verifiedAt()).hasToString("2026-09-21T12:00+09:00");
        verify(objectStorageService).deleteSilently(pending);
    }

    @Test
    void completeIsIdempotentWithoutCallingStorageAgain() {
        StoredFile completed = pending(512L, NOW.minusSeconds(30));
        completed.complete("files/portfolio-image/12/test", NOW);
        when(stateService.getOwned(12, 512)).thenReturn(completed);

        assertThat(service.complete(12, 512).fileId()).isEqualTo(512);
        verify(objectStorageService, never()).inspect(any());
    }

    private static StoredFile pending(Long fileId, Instant createdAt) {
        StoredFile file = StoredFile.pending(
                12,
                "portfolio.png",
                "image/png",
                4L,
                "komit",
                "pending/portfolio-image/12/test");
        ReflectionTestUtils.setField(file, "fileId", fileId);
        ReflectionTestUtils.setField(file, "createdAt", createdAt);
        return file;
    }
}
