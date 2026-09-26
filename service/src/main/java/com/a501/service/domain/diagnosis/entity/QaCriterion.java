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
import org.hibernate.annotations.ColumnDefault;

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

    /**
     * 계약서의 확인 문장인지. 요청서 문장은 {@code false} 다.
     *
     * <p>계약서는 요청서 문장을 복사해 시작하고, 합의해 바꾼 것은 계약서에만 남긴다. 같은 요구사항에 두
     * 문장이 함께 붙으므로 요청서는 이 값으로 자기 문장만 읽는다.
     */
    @ColumnDefault("FALSE")
    @Column(name = "is_contract", nullable = false)
    private boolean contract = false;

    public static QaCriterion of(Integer issueId, String testCase) {
        QaCriterion entity = new QaCriterion();
        entity.issueId = Objects.requireNonNull(issueId, "issueId");
        entity.testCase = Objects.requireNonNull(testCase, "testCase");
        entity.status = "UNRESOLVED";
        entity.statusGroup = "QA_CRITERION_STATUS";
        return entity;
    }

    /** 계약서의 확인 문장. 요청서 문장과 따로 저장해, 요청서를 고쳐도 계약의 통과 조건이 바뀌지 않게 한다. */
    public static QaCriterion forContract(Integer issueId, String testCase) {
        QaCriterion entity = of(issueId, testCase);
        entity.contract = true;
        return entity;
    }

    public void rewrite(String testCase) {
        this.testCase = Objects.requireNonNull(testCase, "testCase");
    }

    /** 계약서를 다시 써서 빠진 계약서 문장. 요청서 문장은 여기로 지우지 않는다. */
    public void dropFromContract(java.time.LocalDateTime deletedAt) {
        if (!contract) {
            throw new IllegalStateException("요청서 문장은 계약서에서 지우지 않는다");
        }
        markDeleted(deletedAt);
    }

    /** 요청서에서 지운 것으로 표시한다. */
    public void dropFromRequirements(java.time.LocalDateTime deletedAt) {
        markDeleted(deletedAt);
    }
}
