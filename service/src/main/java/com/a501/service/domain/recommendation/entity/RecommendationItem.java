package com.a501.service.domain.recommendation.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "recommendation_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationItem extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "batch_id", nullable = false)
    private Integer batchId;

    @Column(name = "rank", nullable = false)
    private Short rank;

    @Column(name = "score", precision = 6, scale = 4)
    private BigDecimal score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_breakdown", columnDefinition = "json")
    private String scoreBreakdown;

    @Column(name = "impressed_at")
    private LocalDateTime impressedAt;

    @Column(name = "profile_viewed_at")
    private LocalDateTime profileViewedAt;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "developer_id", nullable = false)
    private Integer developerId;

    @Column(name = "room_id")
    private Integer roomId;

    @Column(name = "contract_id")
    private Integer contractId;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "status_group", length = 50)
    private String statusGroup;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_snapshot", nullable = false, columnDefinition = "json")
    private String profileSnapshot;

    /**
     * 추천된 개발자 한 명.
     *
     * @param scoreBreakdown  항목별 점수. 왜 이 순위인지 설명하는 근거다 (R-MATCH-05)
     * @param profileSnapshot 추천 시점의 프로필. 알고리즘 성과를 견주는 내부 자료이고 응답에는 나가지 않는다
     */
    public static RecommendationItem of(
            Integer batchId,
            Integer developerId,
            int rank,
            BigDecimal score,
            String scoreBreakdown,
            String profileSnapshot) {
        RecommendationItem entity = new RecommendationItem();
        entity.batchId = Objects.requireNonNull(batchId, "batchId");
        entity.developerId = Objects.requireNonNull(developerId, "developerId");
        entity.rank = (short) rank;
        entity.score = score;
        entity.scoreBreakdown = scoreBreakdown;
        entity.profileSnapshot = Objects.requireNonNull(profileSnapshot, "profileSnapshot");
        return entity;
    }

    /**
     * 상담을 요청해 방이 열렸다. 개발자의 수락이 없어 요청한 그 자리에서 방이 생기므로, 요청했는데 방이 없는 상태는 없다.
     */
    public void requestConsultation(Integer roomId, LocalDateTime requestedAt) {
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
    }

    public boolean isRequested() {
        return roomId != null;
    }
}
