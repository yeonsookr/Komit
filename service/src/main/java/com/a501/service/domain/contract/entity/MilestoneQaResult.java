package com.a501.service.domain.contract.entity;

import com.a501.service.common.persistence.CreatedSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
}
