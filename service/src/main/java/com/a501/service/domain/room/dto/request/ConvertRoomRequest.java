package com.a501.service.domain.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 상담방에서 갈래를 정합니다.
 *
 * @param targetType 이어갈 갈래
 */
@Schema(description = "상담방에서 갈래를 정한다.")
public record ConvertRoomRequest(
        @Schema(
                        description = "이어갈 갈래. CONSULTING 이면 컨설팅 계약으로, DEVELOPMENT 면 개발 계약으로 간다.",
                        example = "DEVELOPMENT",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                ConversionTarget targetType) {

    /** 상담에서 이어갈 수 있는 갈래. 상담으로 되돌아가는 길은 없습니다. */
    public enum ConversionTarget {
        CONSULTING,
        DEVELOPMENT
    }
}
