package com.a501.service.domain.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "files")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile {
    public static final String STATUS_GROUP = "FILE_STATUS";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    private static final String RECORD_STATUS_GROUP = "RECORD_STATUS";
    private static final String RECORD_STATUS_ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "bucket_name", nullable = false, length = 63)
    private String bucketName;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256", length = 64, columnDefinition = "char(64)")
    private String sha256;

    @CreationTimestamp
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "delete_requested_at")
    private Instant deleteRequestedAt;

    @Column(name = "record_status", nullable = false, length = 50)
    private String recordStatus;

    @Column(name = "record_status_group", nullable = false, length = 50)
    private String recordStatusGroup;

    @ColumnDefault("FALSE")
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "status_group", nullable = false, length = 50)
    private String statusGroup;

    public static StoredFile pending(
            Integer userId,
            String originalName,
            String contentType,
            Long sizeBytes,
            String bucketName,
            String storageKey) {
        StoredFile file = new StoredFile();
        file.userId = userId;
        file.originalName = originalName;
        file.contentType = contentType;
        file.sizeBytes = sizeBytes;
        file.bucketName = bucketName;
        file.storageKey = storageKey;
        file.recordStatus = RECORD_STATUS_ACTIVE;
        file.recordStatusGroup = RECORD_STATUS_GROUP;
        file.status = STATUS_PENDING;
        file.statusGroup = STATUS_GROUP;
        return file;
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    public void complete(String finalStorageKey, Instant completedAt) {
        if (!isPending()) {
            return;
        }
        storageKey = finalStorageKey;
        verifiedAt = completedAt;
        status = STATUS_COMPLETED;
    }

    public void fail() {
        if (isPending()) {
            status = STATUS_FAILED;
        }
    }
}
