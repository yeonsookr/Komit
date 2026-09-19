package com.a501.service.domain.recommendation.entity;

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
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "recommendation_batches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationBatch extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "diagnosis_version")
    private Short diagnosisVersion;

    @ColumnDefault("1")
    @Column(name = "sequence_no", nullable = false)
    private Short sequenceNo = 1;

    @Column(name = "parent_batch_id")
    private Integer parentBatchId;

    @Column(name = "algorithm_version", nullable = false, length = 50)
    private String algorithmVersion;

    @Column(name = "variant", length = 30)
    private String variant;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "json")
    private String inputSnapshot;

    @Column(name = "candidate_count", nullable = false)
    private Short candidateCount;

    @Column(name = "viewed_at")
    private LocalDateTime viewedAt;

    @Column(name = "project_id", nullable = false)
    private Integer projectId;

    @Column(name = "requester_user_id", nullable = false)
    private Integer requesterUserId;

    @Column(name = "diagnosis_id")
    private Integer diagnosisId;

    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType;

    @Column(name = "trigger_type_group", nullable = false, length = 50)
    private String triggerTypeGroup;

    @Column(name = "outcome", length = 50)
    private String outcome;

    @Column(name = "outcome_group", length = 50)
    private String outcomeGroup;
}
