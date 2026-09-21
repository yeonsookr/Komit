package com.a501.service.domain.developer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 저장할 가격표 한 줄. 목 명세 {@code WritePriceRequest}와 같습니다. */
@Schema(description = "저장할 가격표 한 줄.")
public record WritePriceRequest(
        @Schema(description = "이미 있는 줄이면 그 식별자, 새로 추가하면 null이다.", example = "61")
        Integer priceId,
        @Schema(description = "작업 종류. 코드 분류 TASK_TYPE의 코드값이다.", example = "DEVELOP")
        @NotNull TaskType taskType,
        @Schema(description = "작업 이름. 50자까지.", example = "로그인 붙이기")
        @NotNull @Size(max = 50) String taskName,
        @Schema(description = "가격. 원 단위 정수다. 1 이상.", example = "400000")
        @NotNull @Min(1) Integer price,
        @Schema(description = "예상 작업 기간(일). 1 이상. 컨설팅 세션이면 비운다.", example = "4")
        @Min(1) Integer periodDays) {
}
