package com.a501.service.domain.developer.dto.response;

import com.a501.service.domain.developer.entity.PriceCatalog;
import io.swagger.v3.oas.annotations.media.Schema;

/** 가격표 한 줄. 목 명세 {@code PriceCatalogItemResponse}와 같으며 개발자 상세와 내 프로필이 같은 모양을 씁니다. */
@Schema(description = "가격표 한 줄. 개발자가 등록한 작업마다 한 행이다.")
public record PriceCatalogItemResponse(
        @Schema(description = "가격표 식별자.", example = "61") Integer priceId,
        @Schema(description = "작업 종류. CONSULT는 컨설팅 세션, DEVELOP은 개발 작업이다.", allowableValues = {"CONSULT", "DEVELOP"}, example = "DEVELOP")
        String taskType,
        @Schema(description = "작업 이름.", example = "로그인 붙이기") String taskName,
        @Schema(description = "가격. 원 단위 정수다.", example = "400000") Integer price,
        @Schema(description = "예상 작업 기간(일). 컨설팅 세션은 언제나 null이다.", example = "4") Integer periodDays) {

    public static PriceCatalogItemResponse from(PriceCatalog row) {
        return new PriceCatalogItemResponse(
                row.getId(),
                row.getTaskType(),
                row.getTaskName(),
                row.getPrice(),
                row.getPeriodDays() == null ? null : row.getPeriodDays().intValue());
    }
}
