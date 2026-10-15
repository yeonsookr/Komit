package com.a501.service.domain.recommendation.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
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

    /**
     * 추천 회차 하나.
     *
     * <p>계기는 서버가 정한다. 이 프로젝트의 첫 회차면 상담 요청, 이미 회차가 있으면 재매칭으로 기록하고 직전 회차를 부모로 잇는다.
     */
    public static RecommendationBatch of(
            Integer projectId,
            Integer requesterUserId,
            Integer diagnosisId,
            Short diagnosisVersion,
            RecommendationBatch previous,
            String algorithmVersion,
            String inputSnapshot,
            int candidateCount) {
        RecommendationBatch entity = new RecommendationBatch();
        entity.projectId = Objects.requireNonNull(projectId, "projectId");
        entity.requesterUserId = Objects.requireNonNull(requesterUserId, "requesterUserId");
        entity.diagnosisId = diagnosisId;
        entity.diagnosisVersion = diagnosisVersion;
        entity.sequenceNo = previous == null ? 1 : (short) (previous.sequenceNo + 1);
        entity.parentBatchId = previous == null ? null : previous.id;
        entity.triggerType = previous == null ? "CONSULT_REQUEST" : "REMATCH";
        entity.triggerTypeGroup = "RECOMMENDATION_TRIGGER";
        entity.algorithmVersion = Objects.requireNonNull(algorithmVersion, "algorithmVersion");
        entity.inputSnapshot = Objects.requireNonNull(inputSnapshot, "inputSnapshot");
        entity.candidateCount = (short) candidateCount;
        return entity;
    }

    /** 의뢰인이 추천 목록을 처음 열었다. 열어 보지 않은 회차가 있으면 새 회차를 만들지 않는다. */
    public void markViewed(LocalDateTime viewedAt) {
        if (this.viewedAt == null) {
            this.viewedAt = Objects.requireNonNull(viewedAt, "viewedAt");
        }
    }

    public boolean isViewed() {
        return viewedAt != null;
    }
}
