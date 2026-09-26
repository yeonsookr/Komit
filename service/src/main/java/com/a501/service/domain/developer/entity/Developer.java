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

/** 개발자. 이 행이 있으면 개발자이며, 사용자당 최대 한 행입니다({@code user_id} unique). 평점과 리뷰 수는 리뷰로만 바뀝니다. */
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

    /** 사용자가 개발자 프로필을 처음 저장할 때 개발자 행을 만듭니다. 평점은 없고 리뷰 수는 0입니다. */
    public static Developer register(Integer userId) {
        Developer developer = new Developer();
        developer.userId = userId;
        return developer;
    }

    /** 새 리뷰의 두 점수를 평균에 더합니다. 평점은 리뷰로만 바뀝니다. (R-REV-01, R-REV-02) */
    public void addReview(int devScore, int expScore) {
        devRating = average(devRating, devReviewCount, devScore);
        devReviewCount += 1;
        explanatoryRating = average(explanatoryRating, explanatoryReviewCount, expScore);
        explanatoryReviewCount += 1;
    }

    private static double average(Double current, int count, int score) {
        return current == null || count == 0 ? score : (current * count + score) / (count + 1);
    }
}
