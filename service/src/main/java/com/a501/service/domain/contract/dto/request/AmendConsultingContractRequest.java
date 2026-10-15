package com.a501.service.domain.contract.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * 컨설팅 계약서 수정 제안.
 *
 * <p><b>보낸 항목만 바뀝니다.</b> 비워 둔 칸은 그대로 두므로, 금액만 고치려고 나머지를 다시 적어
 * 보내지 않아도 됩니다.
 */
@Schema(description = "컨설팅 계약서 수정 제안. 보낸 항목만 바뀐다.")
public record AmendConsultingContractRequest(
        @Schema(description = "바꿀 「다룰 항목」.") @Size(max = 255) String topic,
        @Schema(description = "바꿀 세션 일시.") OffsetDateTime scheduledAt,
        @Schema(description = "바꿀 세션 시간(분).") @PositiveOrZero Integer durationMinutes,
        @Schema(description = "바꿀 세션 금액.") @PositiveOrZero Integer price,
        @Schema(description = "바꿀 상세 설명. 보내지 않으면 그대로 두고, 빈 문자열이면 지운다.") String detail,
        @Schema(description = "계약서와 함께 남길 전달 메시지.") @Size(max = 300) String message) {}
