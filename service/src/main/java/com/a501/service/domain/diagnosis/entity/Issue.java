package com.a501.service.domain.diagnosis.entity;

import com.a501.service.common.persistence.CreatedSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "issues")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Issue extends CreatedSoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "inspection_id", nullable = false)
    private Integer inspectionId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "location", length = 400)
    private String location;

    @Column(name = "is_input")
    private Boolean input;

    @Column(name = "category_code_id", nullable = false)
    private Integer categoryCodeId;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "status_group", nullable = false, length = 50)
    private String statusGroup;

    @Column(name = "severity", nullable = false, length = 50)
    private String severity;

    @Column(name = "severity_group", nullable = false, length = 50)
    private String severityGroup;

    @Column(name = "fixed_version")
    private Short fixedVersion;

    @Column(name = "origin_issue_id")
    private Integer originIssueId;

    @Column(name = "fingerprint", length = 120)
    private String fingerprint;
}
