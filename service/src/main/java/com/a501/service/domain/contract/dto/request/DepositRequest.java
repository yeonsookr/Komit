package com.a501.service.domain.contract.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 예치 요청.
 *
 * <p>출금 계좌는 담지 않습니다. 회원가입 때 발급한 의뢰인의 계좌에서 빠지며 별도 계좌 선택은
 * 없습니다. (`R-PAY-09`)
 */
@Schema(description = "계약 금액 예치 요청.")
public record DepositRequest(
        @Schema(description = "회원가입 때 발급한 출금 계좌의 결제 수단 식별자.", example = "9")
                @NotNull
                @Positive
                Integer paymentMethodId,
        @Schema(
                        description = "같은 결제가 두 번 나가지 않게 하는 키. 재시도할 때 같은 값을 보낸다.",
                        example = "dep-130-9f2c")
                @NotBlank
                String idempotencyKey) {}
