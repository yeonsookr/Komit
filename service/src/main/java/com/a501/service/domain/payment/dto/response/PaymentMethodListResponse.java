package com.a501.service.domain.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "예치에 쓸 결제 수단.")
public record PaymentMethodListResponse(
        @Schema(description = "회원가입 때 발급한 출금 계좌 한 건이다. (R-PAY-09)")
                List<PaymentMethodResponse> content) {}
