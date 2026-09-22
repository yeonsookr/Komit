package com.a501.service.domain.diagnosis.dto.request;

import com.a501.service.domain.diagnosis.IssueSeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "사용자가 직접 적는 요구사항 추가 요청.")
public record CreateIssueRequest(
        @Schema(description = "요구사항 문장.", example = "장바구니에서 수량을 바꿀 수 있게 해주세요")
        @NotBlank @Size(max = 200) String title,
        @Schema(description = "자세한 설명. 적지 않아도 된다.") @Size(max = 1000) String detail,
        @Schema(description = "결함 분류 식별자.", example = "73") @NotNull Integer categoryCodeId,
        @Schema(description = "위험도. LOW 낮음, MEDIUM 보통, HIGH 높음.", example = "HIGH")
        @NotNull IssueSeverity severity) {}
