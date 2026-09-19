package com.a501.service.domain.developer.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "developers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Developer extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "explanatory_rating")
    private Double explanatoryRating;

    @Column(name = "dev_rating")
    private Double devRating;

    @ColumnDefault("0")
    @Column(name = "explanatory_review_count", nullable = false)
    private Integer explanatoryReviewCount = 0;

    @ColumnDefault("0")
    @Column(name = "dev_review_count", nullable = false)
    private Integer devReviewCount = 0;

    @Column(name = "user_id", nullable = false, unique = true)
    private Integer userId;
}
