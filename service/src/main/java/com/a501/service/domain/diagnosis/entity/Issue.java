package com.a501.service.domain.diagnosis.entity;

import com.a501.service.common.persistence.CreatedSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
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

    public static Issue of(
            Integer inspectionId,
            String title,
            String detail,
            String location,
            Integer categoryCodeId,
            String severity,
            boolean input,
            String fingerprint) {
        Issue entity = new Issue();
        entity.inspectionId = Objects.requireNonNull(inspectionId, "inspectionId");
        entity.title = Objects.requireNonNull(title, "title");
        entity.detail = detail;
        entity.location = location;
        entity.categoryCodeId = Objects.requireNonNull(categoryCodeId, "categoryCodeId");
        entity.severity = Objects.requireNonNull(severity, "severity");
        entity.severityGroup = "ISSUE_SEVERITY";
        entity.input = input;
        entity.fingerprint = fingerprint;
        entity.status = "UNRESOLVED";
        entity.statusGroup = "ISSUE_STATUS";
        return entity;
    }

    /** 지난 회차에서 이어진 결함임을 남긴다. 최초로 발견한 결함을 가리켜 회차를 거슬러 올라갈 수 있게 한다. (R-SCAN-05) */
    public void continueFrom(Integer originIssueId) {
        this.originIssueId = Objects.requireNonNull(originIssueId, "originIssueId");
    }

    /**
     * 이 결함이 사라진 것을 확인한 회차를 적는다. 발견한 회차의 행에 적으므로 지난 회차 기록은 그대로 남는다.
     */
    public void markFixedAt(Short diagnosisVersion) {
        fixedVersion = Objects.requireNonNull(diagnosisVersion, "diagnosisVersion");
    }
}
