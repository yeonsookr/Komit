package com.a501.service.domain.report.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "contract_id", nullable = false)
    private Integer contractId;

    @Column(name = "reporter_user_id", nullable = false)
    private Integer reporterUserId;

    @Column(name = "file_id")
    private Integer fileId;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "target_user_id", nullable = false)
    private Integer targetUserId;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "status_group", nullable = false, length = 50)
    private String statusGroup;
}
