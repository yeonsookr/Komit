package com.a501.service.common.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@MappedSuperclass
public abstract class SoftDeleteEntity extends AuditedEntity {
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "record_status", nullable = false, length = 50)
    private String recordStatus = "ACTIVE";

    @Column(name = "record_status_group", nullable = false, length = 50)
    private String recordStatusGroup = "RECORD_STATUS";

    @ColumnDefault("false")
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;
}
