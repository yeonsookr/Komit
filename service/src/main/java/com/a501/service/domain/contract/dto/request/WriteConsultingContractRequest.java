package com.a501.service.domain.contract.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

@Schema(description = "컨설팅 계약서 작성 요청.")
public record WriteConsultingContractRequest(
        @Schema(
                        description = "이 세션에서 다룰 것. 한 줄로 적는다.",
                        example = "회원탈퇴 경로와 결제 간헐 실패를 함께 봅니다")
                @NotBlank
                @Size(max = 255)
                String topic,
        @Schema(description = "세션을 할 일시.", example = "2026-08-29T14:00:00+09:00") @NotNull
                OffsetDateTime scheduledAt,
        @Schema(description = "세션 시간(분).", example = "30") @NotNull @PositiveOrZero
                Integer durationMinutes,
        @Schema(description = "세션 금액. 원 단위 정수다.", example = "50000") @NotNull @PositiveOrZero
                Integer price,
        @Schema(description = "상세 설명. 적지 않아도 된다.") String detail,
        @Schema(description = "계약서와 함께 상대에게 남길 전달 메시지.") @Size(max = 300)
                String message) {}
