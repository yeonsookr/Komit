package com.a501.service.domain.developer.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 저장할 작업물 하나. 목 명세 {@code WritePortfolioItemRequest}와 같습니다.
 * 길이 제약은 ERD {@code portfolio_items}의 컬럼 길이(제목 200, 설명 500, 링크 2048)입니다.
 */
@Schema(description = "저장할 작업물 하나.")
public record WritePortfolioItemRequest(
        @Schema(description = "이미 있는 작업물이면 그 식별자, 새로 추가하면 null이다.", example = "7")
        Integer portfolioItemId,
        @Schema(description = "작업물 제목. 200자까지.", example = "미미헤어샵 예약")
        @NotNull @Size(max = 200) String title,
        @Schema(description = "설명. 500자까지 적을 수 있고, 적지 않으면 null이다.", example = "예약이 저장되지 않던 문제를 고쳤습니다.")
        @JsonProperty(required = true) @Size(max = 500) String description,
        @Schema(description = "배포 사이트나 저장소 주소. 2048자까지. 없으면 null이다.", example = "https://hairshop-mimi.com")
        @JsonProperty(required = true) @Size(max = 2048) String linkUrl,
        @Schema(description = "올려 둔 사진의 파일 식별자. 기존 식별자면 유지, 새 식별자면 교체, null이면 제거다.", example = "512")
        @JsonProperty(required = true) Integer thumbnailFileId) {
}
