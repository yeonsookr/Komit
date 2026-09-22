package com.a501.service.domain.file.service;

import com.a501.service.config.StorageProperties;
import com.a501.service.domain.file.entity.StoredFile;
import io.minio.CopyObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.SourceObject;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** MinIO SDK 호출을 파일 도메인의 한 곳에 모읍니다. */
@Slf4j
@Service
public class ObjectStorageService {
    private final MinioClient internalClient;
    private final MinioClient publicClient;
    private final StorageProperties properties;

    public ObjectStorageService(
            @Qualifier("minioInternalClient") MinioClient internalClient,
            @Qualifier("minioPublicClient") MinioClient publicClient,
            StorageProperties properties) {
        this.internalClient = internalClient;
        this.publicClient = publicClient;
        this.properties = properties;
    }

    public String issueUploadUrl(StoredFile file) {
        return presignedUrl(Http.Method.PUT, file, properties.uploadUrlTtl().toSeconds());
    }

    public String issueReadUrl(StoredFile file) {
        return presignedUrl(Http.Method.GET, file, properties.readUrlTtl().toSeconds());
    }

    public Optional<StoredObjectMetadata> inspect(StoredFile file) {
        try {
            StatObjectResponse response = internalClient.statObject(StatObjectArgs.builder()
                    .bucket(file.getBucketName())
                    .object(file.getStorageKey())
                    .build());
            return Optional.of(new StoredObjectMetadata(response.size(), response.contentType()));
        } catch (ErrorResponseException exception) {
            if (isMissing(exception)) {
                return Optional.empty();
            }
            throw storageFailure("파일 정보를 확인하지 못했습니다.", exception);
        } catch (Exception exception) {
            throw storageFailure("파일 정보를 확인하지 못했습니다.", exception);
        }
    }

    /** 검증된 임시 객체를 최종 키로 복사합니다. 완료 뒤에는 예전에 발급한 PUT 주소로 파일을 바꿀 수 없습니다. */
    public String promote(StoredFile file) {
        String finalKey = finalKey(file.getStorageKey());
        try {
            internalClient.copyObject(CopyObjectArgs.builder()
                    .bucket(file.getBucketName())
                    .object(finalKey)
                    .source(SourceObject.builder()
                            .bucket(file.getBucketName())
                            .object(file.getStorageKey())
                            .build())
                    .build());
            return finalKey;
        } catch (Exception exception) {
            throw storageFailure("파일을 최종 저장 위치로 옮기지 못했습니다.", exception);
        }
    }

    public void delete(StoredFile file) {
        delete(file.getBucketName(), file.getStorageKey());
    }

    public void delete(String bucket, String storageKey) {
        try {
            internalClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .build());
        } catch (Exception exception) {
            throw storageFailure("파일을 지우지 못했습니다.", exception);
        }
    }

    public void deleteSilently(StoredFile file) {
        try {
            delete(file);
        } catch (IllegalStateException exception) {
            log.warn("Storage cleanup failed. fileId={}", file.getFileId());
        }
    }

    public void deleteSilently(String bucket, String storageKey, Long fileId) {
        try {
            delete(bucket, storageKey);
        } catch (IllegalStateException exception) {
            log.warn("Storage cleanup failed. fileId={}", fileId);
        }
    }

    private String presignedUrl(Http.Method method, StoredFile file, long ttlSeconds) {
        try {
            return publicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method)
                    .bucket(file.getBucketName())
                    .object(file.getStorageKey())
                    .expiry(Math.toIntExact(ttlSeconds), TimeUnit.SECONDS)
                    .build());
        } catch (Exception exception) {
            throw storageFailure("파일 임시 주소를 발급하지 못했습니다.", exception);
        }
    }

    private static String finalKey(String pendingKey) {
        if (!pendingKey.startsWith("pending/")) {
            throw new IllegalStateException("임시 저장 키 형식이 올바르지 않습니다.");
        }
        return "files/" + pendingKey.substring("pending/".length());
    }

    private static boolean isMissing(ErrorResponseException exception) {
        String code = exception.errorResponse().code();
        return "NoSuchKey".equals(code) || "NoSuchObject".equals(code);
    }

    private static IllegalStateException storageFailure(String message, Exception cause) {
        return new IllegalStateException(message, cause);
    }

    public record StoredObjectMetadata(long sizeBytes, String contentType) {
    }
}
