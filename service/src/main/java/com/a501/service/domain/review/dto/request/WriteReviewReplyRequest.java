package com.a501.service.domain.review.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 리뷰 답글 요청. 목 명세 {@code WriteReviewReplyRequest}와 같습니다. */
@Schema(description = "리뷰 답글 요청.")
public record WriteReviewReplyRequest(
        @Schema(description = "답글 내용. 1000자까지 받는다.", example = "감사합니다. 도메인 만료 전에 미리 알려드리겠습니다.")
        @NotNull
        @Size(min = 1, message = "{komit.minLength}")   // 목: min_length=1
        @Size(max = 1000)                               // 목: max_length=1000
        String reply) {
}
