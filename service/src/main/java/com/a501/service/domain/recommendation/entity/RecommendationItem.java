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
}
