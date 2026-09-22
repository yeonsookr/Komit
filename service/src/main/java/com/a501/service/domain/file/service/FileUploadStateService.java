package com.a501.service.domain.file.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.config.StorageProperties;
import com.a501.service.domain.file.FileErrorCode;
import com.a501.service.domain.file.entity.StoredFile;
import com.a501.service.domain.file.repository.StoredFileRepository;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DB 상태 변경만 담당해 MinIO 네트워크 호출과 트랜잭션을 분리합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileUploadStateService {
    private final StoredFileRepository storedFileRepository;
    private final StorageProperties storageProperties;

    @Transactional
    public StoredFile createPending(
            Integer userId,
            String originalName,
            String contentType,
            Long sizeBytes,
            String storageKey,
            String purpose) {
        return storedFileRepository.saveAndFlush(StoredFile.pending(
                userId,
                originalName,
                contentType,
                sizeBytes,
                storageProperties.bucket(),
                storageKey,
                purpose));
    }

    public StoredFile getOwned(Integer userId, Integer uploadId) {
        StoredFile file = storedFileRepository.findByFileIdAndDeletedFalse(uploadId.longValue())
                .orElseThrow(() -> new BusinessException(FileErrorCode.UPLOAD_NOT_FOUND));
        assertOwner(file, userId);
        return file;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public StoredFile complete(Integer userId, Integer uploadId, String finalStorageKey, Instant completedAt) {
        StoredFile file = locked(uploadId);
        assertOwner(file, userId);
        if (file.isCompleted()) {
            return file;
        }
        if (!file.isPending()) {
            throw new BusinessException(FileErrorCode.UPLOAD_NOT_COMPLETED);
        }
        if (!completedAt.isBefore(file.getCreatedAt().plus(storageProperties.uploadUrlTtl()))) {
            file.fail();
            throw new BusinessException(FileErrorCode.UPLOAD_EXPIRED);
        }
        file.complete(finalStorageKey, completedAt);
        return file;
    }

    @Transactional
    public void fail(Integer userId, Integer uploadId) {
        StoredFile file = locked(uploadId);
        assertOwner(file, userId);
        file.fail();
    }

    private StoredFile locked(Integer uploadId) {
        return storedFileRepository.findByFileIdForUpdate(uploadId.longValue())
                .orElseThrow(() -> new BusinessException(FileErrorCode.UPLOAD_NOT_FOUND));
    }

    private static void assertOwner(StoredFile file, Integer userId) {
        if (!Objects.equals(file.getUserId(), userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}
