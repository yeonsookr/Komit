package com.a501.service.domain.review.dto.response;

import com.a501.service.common.time.SeoulTime;
import com.a501.service.domain.review.entity.Review;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** 계약을 마친 의뢰인이 남긴 리뷰 하나. 목 명세 {@code ReviewResponse}와 같습니다. */
@Schema(description = "계약을 마친 의뢰인이 남긴 리뷰 하나.")
public record ReviewResponse(
        @Schema(description = "리뷰 식별자.", example = "88") Integer reviewId,
        @Schema(description = "리뷰를 쓴 의뢰인의 표시 이름. 서버가 가려서 내보낸다. 탈퇴한 의뢰인이면 '탈퇴한 사용자'다.", example = "김O미")
        String clientName,
        @Schema(description = "이 리뷰가 나온 계약의 종류.", allowableValues = {"CONSULTING", "DEVELOPMENT"}, example = "DEVELOPMENT")
        String contractType,
        @Schema(description = "개발력 점수. 1에서 5 사이.", example = "5") Integer devScore,
        @Schema(description = "설명력 점수. 1에서 5 사이.", example = "5") Integer expScore,
        @Schema(description = "리뷰 내용. 점수만 주고 글을 안 썼으면 null이다.", example = "말이 잘 통했습니다.") String content,
        @Schema(description = "개발자가 단 답글. 없으면 null이다.") String reply,
        @Schema(description = "작성 시각.", example = "2026-08-19T14:20:00+09:00") @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") OffsetDateTime createdAt) {

    public static ReviewResponse of(Review review, String clientName, String contractType) {
        return new ReviewResponse(
                review.getId(),
                clientName,
                contractType,
                review.getDevScore(),
                review.getExpScore(),
                review.getContent(),
                review.getReply(),
                SeoulTime.of(review.getCreatedAt()));
    }
}
