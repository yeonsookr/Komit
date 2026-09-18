package com.a501.service.domain.contract.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "contract_templates")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @ColumnDefault("TRUE")
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreatedDate
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ColumnDefault("FALSE")
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "created_by_admin_id", nullable = false)
    private Integer createdByAdminId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @ColumnDefault("'CONTRACT_TEMPLATE_TYPE'")
    @Column(name = "type_group", nullable = false, length = 50)
    private String typeGroup = "CONTRACT_TEMPLATE_TYPE";

    @ColumnDefault("'ACTIVE'")
    @Column(name = "record_status", nullable = false, length = 50)
    private String recordStatus = "ACTIVE";

    @ColumnDefault("'RECORD_STATUS'")
    @Column(name = "record_status_group", nullable = false, length = 50)
    private String recordStatusGroup = "RECORD_STATUS";
}
