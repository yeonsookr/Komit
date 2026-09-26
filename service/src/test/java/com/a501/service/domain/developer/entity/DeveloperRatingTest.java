package com.a501.service.domain.developer.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 평점은 리뷰로만 바뀌고, 두 축을 따로 평균낸다 (R-REV-01, R-REV-02). */
class DeveloperRatingTest {
    @Test
    @DisplayName("첫 리뷰의 점수가 곧 평점이다")
    void firstReviewSetsTheRating() {
        Developer developer = Developer.register(1);

        developer.addReview(5, 3);

        assertThat(developer.getDevRating()).isEqualTo(5.0);
        assertThat(developer.getExplanatoryRating()).isEqualTo(3.0);
        assertThat(developer.getDevReviewCount()).isEqualTo(1);
        assertThat(developer.getExplanatoryReviewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("다음 리뷰부터는 지금까지의 평균에 더한다")
    void laterReviewsAverage() {
        Developer developer = Developer.register(1);

        developer.addReview(5, 3);
        developer.addReview(4, 4);
        developer.addReview(3, 5);

        assertThat(developer.getDevRating()).isCloseTo(4.0, within(1e-9));
        assertThat(developer.getExplanatoryRating()).isCloseTo(4.0, within(1e-9));
        assertThat(developer.getDevReviewCount()).isEqualTo(3);
    }
}
