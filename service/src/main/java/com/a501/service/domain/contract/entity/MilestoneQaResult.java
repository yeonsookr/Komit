package com.a501.service.domain.contract.entity;

import com.a501.service.common.persistence.CreatedSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(
        name = "milestone_qa_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_milestone_qa_results_milestone_qa_criterion",
                columnNames = {"milestone_id", "qa_criterion_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MilestoneQaResult extends CreatedSoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ColumnDefault("FALSE")
    @Column(name = "is_passed", nullable = false)
    private boolean passed = false;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "milestone_id", nullable = false)
    private Integer milestoneId;

    @Column(name = "qa_criterion_id", nullable = false)
    private Integer qaCriterionId;

    /** 작업에 확인 문장 하나를 건다. 통과 체크는 여기에 남는다. */
    public static MilestoneQaResult of(Integer milestoneId, Integer qaCriterionId) {
        MilestoneQaResult entity = new MilestoneQaResult();
        entity.milestoneId = Objects.requireNonNull(milestoneId, "milestoneId");
        entity.qaCriterionId = Objects.requireNonNull(qaCriterionId, "qaCriterionId");
        return entity;
    }

    /** 의뢰인이 통과를 체크했거나 작업이 자동 통과됐다. 체크는 되돌리지 않는다. */
    public void pass() {
        this.passed = true;
    }
}
