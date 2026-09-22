package com.a501.service.domain.developer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 작업 유형별 가격 한 줄. 개발자가 등록한 가격표의 유형별 최저가입니다. 목 명세 {@code TaskPriceResponse}와 같습니다. */
@Schema(description = "작업 유형별 가격 한 줄. 개발자가 등록한 가격표의 유형별 최저가다.")
public record TaskPriceResponse(
        @Schema(description = "작업 종류. CONSULT는 컨설팅, DEVELOP은 개발이다.", allowableValues = {"CONSULT", "DEVELOP"}, example = "CONSULT")
        String taskType,
        @Schema(description = "해당 유형에서 이 개발자가 받는 최저 금액. 원 단위 정수다.", example = "50000") Long price) {
}
