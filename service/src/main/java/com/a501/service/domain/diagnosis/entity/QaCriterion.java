package com.a501.service.domain.diagnosis.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
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
@Table(name = "qa_criteria")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QaCriterion extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "issue_id")
    private Integer issueId;

    @Column(name = "test_case", length = 500)
    private String testCase;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "status_group", nullable = false, length = 50)
    private String statusGroup;

    @Column(name = "condition", length = 500)
    private String condition;

    public static QaCriterion of(Integer issueId, String testCase) {
        QaCriterion entity = new QaCriterion();
        entity.issueId = Objects.requireNonNull(issueId, "issueId");
        entity.testCase = Objects.requireNonNull(testCase, "testCase");
        entity.status = "UNRESOLVED";
        entity.statusGroup = "QA_CRITERION_STATUS";
        return entity;
    }

    public void rewrite(String testCase) {
        this.testCase = Objects.requireNonNull(testCase, "testCase");
    }

    /** 요청서에서 지운 것으로 표시한다. */
    public void dropFromRequirements(java.time.LocalDateTime deletedAt) {
        markDeleted(deletedAt);
    }
}
