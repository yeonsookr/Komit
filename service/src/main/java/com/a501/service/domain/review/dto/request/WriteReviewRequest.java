package com.a501.service.domain.review.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "계약을 마친 뒤 남기는 리뷰.")
public record WriteReviewRequest(
        @Schema(description = "개발력 점수. 1에서 5 사이다.", example = "5") @NotNull @Min(1) @Max(5) Integer devScore,
        @Schema(description = "설명력 점수. 1에서 5 사이다.", example = "5") @NotNull @Min(1) @Max(5) Integer expScore,
        @Schema(description = "리뷰 내용. 1,000자까지 받는다(R-REV-04). 점수만 주고 넘어가도 된다.", example = "말이 잘 통했습니다.")
                @Size(max = 1000)
                String content) {}
