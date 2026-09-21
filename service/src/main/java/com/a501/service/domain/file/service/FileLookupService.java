package com.a501.service.domain.file.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.config.StorageProperties;
import com.a501.service.domain.file.FileErrorCode;
import com.a501.service.domain.file.entity.StoredFile;
import com.a501.service.domain.file.repository.StoredFileRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인이 파일을 리소스에 붙이기 전에 확인하는 창구입니다. 다른 도메인은 {@code StoredFileRepository}를 직접 쓰지 않고
 * 이 서비스만 호출합니다. (BACKEND_CONVENTIONS 7.1)
 *
 * <p>업로드 발급·완료 API는 파일 API 작업에서 붙습니다. 이 서비스는 파일을 붙일 수 있는지 판정하고, 원래 파일명과
 * 화면에서 잠시 사용할 수 있는 읽기 주소를 돌려줍니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileLookupService {
    /** {@code files.status} 코드값. 올리기 완료 검증을 통과한 파일만 어딘가에 붙일 수 있습니다. */
    static final String COMPLETED = "COMPLETED";

    private final StoredFileRepository storedFileRepository;
    private final MinioClient minioClient;
    private final StorageProperties storageProperties;

    /**
     * 파일들이 모두 호출자가 올린 완료 상태의 파일인지 확인합니다. null은 "붙이지 않음"이라 건너뜁니다.
     *
     * @throws BusinessException {@link FileErrorCode#FILE_NOT_ATTACHABLE} 하나라도 없거나, 완료 전이거나, 남의 파일일 때
     */
    public void assertAttachable(Integer userId, Collection<Integer> fileIds) {
        Set<Long> wanted = ids(fileIds);
        if (wanted.isEmpty()) {
            return;
        }
        long attachable = storedFileRepository.findByFileIdInAndDeletedFalse(wanted).stream()
                .filter(file -> isAttachableBy(file, userId))
                .map(StoredFile::getFileId)
                .distinct()
                .count();
        if (attachable != wanted.size()) {
            throw new BusinessException(FileErrorCode.FILE_NOT_ATTACHABLE);
        }
    }

    /** 파일 식별자별 원래 파일명. 없는 파일은 결과에 빠집니다. */
    public Map<Integer, String> findOriginalNames(Collection<Integer> fileIds) {
        Set<Long> wanted = ids(fileIds);
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return storedFileRepository.findByFileIdInAndDeletedFalse(wanted).stream()
                .collect(Collectors.toMap(file -> file.getFileId().intValue(), StoredFile::getOriginalName,
                        (first, second) -> first));
    }

    /** 파일 식별자별 임시 읽기 주소. 없는 파일은 결과에 빠지며 저장소 주소는 한 번의 DB 조회 뒤 일괄 생성합니다. */
    public Map<Integer, String> findReadUrls(Collection<Integer> fileIds) {
        Set<Long> wanted = ids(fileIds);
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return storedFileRepository.findByFileIdInAndDeletedFalse(wanted).stream()
                .filter(file -> COMPLETED.equals(file.getStatus()))
                .collect(Collectors.toMap(
                        file -> file.getFileId().intValue(),
                        this::issueReadUrl,
                        (first, second) -> first));
    }

    private String issueReadUrl(StoredFile file) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(file.getBucketName())
                    .object(file.getStorageKey())
                    .expiry(Math.toIntExact(storageProperties.readUrlTtl().toSeconds()), TimeUnit.SECONDS)
                    .build());
        } catch (MinioException exception) {
            throw new IllegalStateException("파일 읽기 주소를 발급하지 못했습니다.", exception);
        }
    }

    private static boolean isAttachableBy(StoredFile file, Integer userId) {
        return COMPLETED.equals(file.getStatus()) && Objects.equals(file.getUserId(), userId);
    }

    private static Set<Long> ids(Collection<Integer> fileIds) {
        if (fileIds == null) {
            return Set.of();
        }
        return fileIds.stream().filter(Objects::nonNull).map(Integer::longValue).collect(Collectors.toSet());
    }
}
