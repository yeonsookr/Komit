package com.a501.service.domain.file.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.time.SeoulTime;
import com.a501.service.config.StorageProperties;
import com.a501.service.domain.file.FileErrorCode;
import com.a501.service.domain.file.dto.request.CreateUploadRequest;
import com.a501.service.domain.file.dto.request.UploadPurpose;
import com.a501.service.domain.file.dto.response.UploadTargetResponse;
import com.a501.service.domain.file.dto.response.UploadedFileResponse;
import com.a501.service.domain.file.entity.StoredFile;
import com.a501.service.domain.file.service.ObjectStorageService.StoredObjectMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 파일 업로드 시작과 완료를 조정합니다. MinIO 호출은 DB 트랜잭션 밖에서 수행합니다. */
@Service
public class FileUploadService {
    private final FileUploadStateService stateService;
    private final ObjectStorageService objectStorageService;
    private final StorageProperties storageProperties;
    private final Clock clock;

    @Autowired
    public FileUploadService(
            FileUploadStateService stateService,
            ObjectStorageService objectStorageService,
            StorageProperties storageProperties) {
        this(stateService, objectStorageService, storageProperties, Clock.systemUTC());
    }

    FileUploadService(
            FileUploadStateService stateService,
            ObjectStorageService objectStorageService,
            StorageProperties storageProperties,
            Clock clock) {
        this.stateService = stateService;
        this.objectStorageService = objectStorageService;
        this.storageProperties = storageProperties;
        this.clock = clock;
    }

    public UploadTargetResponse start(Integer userId, CreateUploadRequest request) {
        validatePolicy(request);
        String storageKey = "pending/%s/%d/%s".formatted(
                request.purpose().keySegment(), userId, UUID.randomUUID());
        StoredFile file = stateService.createPending(
                userId,
                request.originalName(),
                request.contentType(),
                request.sizeBytes(),
                storageKey);
        try {
            String uploadUrl = objectStorageService.issueUploadUrl(file);
            return new UploadTargetResponse(
                    Math.toIntExact(file.getFileId()),
                    uploadUrl,
                    file.getCreatedAt().plus(storageProperties.uploadUrlTtl())
                            .atZone(SeoulTime.ZONE).toOffsetDateTime(),
                    Map.of("Content-Type", request.contentType()));
        } catch (RuntimeException exception) {
            stateService.fail(userId, Math.toIntExact(file.getFileId()));
            throw exception;
        }
    }

    public UploadedFileResponse complete(Integer userId, Integer uploadId) {
        StoredFile file = stateService.getOwned(userId, uploadId);
        if (file.isCompleted()) {
            return UploadedFileResponse.from(file);
        }
        if (!file.isPending()) {
            throw new BusinessException(FileErrorCode.UPLOAD_NOT_COMPLETED);
        }

        Instant now = clock.instant();
        if (!now.isBefore(file.getCreatedAt().plus(storageProperties.uploadUrlTtl()))) {
            objectStorageService.deleteSilently(file);
            stateService.fail(userId, uploadId);
            throw new BusinessException(FileErrorCode.UPLOAD_EXPIRED);
        }

        StoredObjectMetadata metadata = objectStorageService.inspect(file)
                .orElseThrow(() -> new BusinessException(FileErrorCode.UPLOAD_NOT_COMPLETED));
        if (metadata.sizeBytes() != file.getSizeBytes()) {
            reject(file, userId, uploadId, FileErrorCode.UPLOAD_SIZE_MISMATCH);
        }
        if (!Objects.equals(metadata.contentType(), file.getContentType())) {
            reject(file, userId, uploadId, FileErrorCode.UPLOAD_TYPE_MISMATCH);
        }

        String finalKey = objectStorageService.promote(file);
        try {
            StoredFile completed = stateService.complete(userId, uploadId, finalKey, now);
            objectStorageService.deleteSilently(file);
            return UploadedFileResponse.from(completed);
        } catch (BusinessException exception) {
            objectStorageService.deleteSilently(file.getBucketName(), finalKey, file.getFileId());
            throw exception;
        }
    }

    private void validatePolicy(CreateUploadRequest request) {
        if (request.sizeBytes() > maxBytesFor(request.purpose())) {
            throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE);
        }
        if (!request.purpose().accepts(request.contentType())) {
            throw new BusinessException(FileErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }

    /** R-FILE-01: 상한은 용도별입니다. 녹음만 통화 길이에 비례해 커집니다. */
    private long maxBytesFor(UploadPurpose purpose) {
        return purpose == UploadPurpose.SESSION_RECORDING
                ? storageProperties.sessionRecordingMaxBytes()
                : storageProperties.maxFileBytes();
    }

    private void reject(
            StoredFile file,
            Integer userId,
            Integer uploadId,
            FileErrorCode errorCode) {
        objectStorageService.deleteSilently(file);
        stateService.fail(userId, uploadId);
        throw new BusinessException(errorCode);
    }
}
