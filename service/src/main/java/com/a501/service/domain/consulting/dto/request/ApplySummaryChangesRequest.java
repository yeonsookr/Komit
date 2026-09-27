package com.a501.service.domain.consulting.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 요약의 변경 후보를 요청서에 반영하기.
 *
 * @param changeIds 반영할 후보
 */
@Schema(description = "변경 후보 반영 요청")
public record ApplySummaryChangesRequest(
        @Schema(
                        description = "반영할 변경 후보 식별자. 한 건 이상이다.",
                        example = "[1, 2]",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotEmpty
                List<@NotNull Integer> changeIds) {}
