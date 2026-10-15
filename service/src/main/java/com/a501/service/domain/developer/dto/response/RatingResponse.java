package com.a501.service.domain.developer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 평점 하나와 그 근거가 된 리뷰 수. 목 명세 {@code RatingResponse}와 같습니다.
 * 리뷰 하나는 개발력과 설명력을 둘 다 받으므로 두 평점의 {@code reviewCount}는 언제나 같습니다.
 */
@Schema(description = "평점 하나와 그 근거가 된 리뷰 수.")
public record RatingResponse(
        @Schema(description = "평균 평점. 받은 리뷰가 없으면 null이다.", example = "4.4") Double score,
        @Schema(description = "집계에 들어간 리뷰 수.", example = "20") Integer reviewCount) {

    public static RatingResponse of(Double score, Integer reviewCount) {
        return new RatingResponse(score, reviewCount == null ? 0 : reviewCount);
    }
}
