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

    /** 검사가 찾지 못했지만 의뢰인이 고치고 싶어 직접 적은 것. 위치는 코드에서 찾은 값이 아니라 비운다. */
    public static Issue writtenBySelf(
            Integer inspectionId,
            String title,
            String detail,
            Integer categoryCodeId,
            String severity) {
        return of(inspectionId, title, detail, null, categoryCodeId, severity, false, null);
    }

    /** 요청서에서 고쳐 쓴 내용을 반영한다. 위치와 지문은 검사가 낸 값이라 건드리지 않는다. */
    public void rewrite(String title, String detail, Integer categoryCodeId, String severity) {
        this.title = Objects.requireNonNull(title, "title");
        this.detail = detail;
        this.categoryCodeId = Objects.requireNonNull(categoryCodeId, "categoryCodeId");
        this.severity = Objects.requireNonNull(severity, "severity");
    }

    /**
     * 요청서에 담긴 것으로 표시한다. 아직 고르지 않은 결함과 갈라야 화면이 요청서 본문을 그릴 수 있다.
     *
     * <p>이미 계약으로 넘어간 요구사항은 그대로 둔다. 계약 상태가 제안으로 되돌아가면 안 된다.
     */
    public void putIntoRequirements() {
        if (isContracted()) {
            return;
        }
        status = "SUGGESTED";
        fixedVersion = null;
    }

    /**
     * 의뢰인이 스스로 고친 것으로 내린다. 해결한 것은 목록 아래로 가고 요청서에 담기지 않는다. 같은 상태를 다시 보내도 결과가 같다.
     */
    public void resolveBySelf(Short diagnosisVersion) {
        status = "RESOLVED";
        fixedVersion = Objects.requireNonNull(diagnosisVersion, "diagnosisVersion");
    }

    /** 잘못 눌렀을 때 미해결로 되돌린다. 사라진 것을 확인한 회차도 함께 지운다. */
    public void reopen() {
        status = "UNRESOLVED";
        fixedVersion = null;
    }

    public boolean isContracted() {
        return "CONTRACTED".equals(status);
    }

    public boolean isResolved() {
        return "RESOLVED".equals(status);
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
