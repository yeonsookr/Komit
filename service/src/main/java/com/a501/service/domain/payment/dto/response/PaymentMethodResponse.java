package com.a501.service.domain.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "예치에 쓸 결제 수단 한 건.")
public record PaymentMethodResponse(
        @Schema(description = "결제 수단 식별자.", example = "9") Integer paymentMethodId,
        @Schema(description = "수단 종류. 지금은 실시간 계좌이체만 내려간다.", example = "REALTIME_TRANSFER")
                MethodType methodType,
        @Schema(description = "가린 계좌번호. 화면에 그대로 보여준다.", example = "싸피은행 ···· 8842")
                String maskedNumber,
        @Schema(description = "기본 수단인지. 예치 화면이 처음에 고르는 것이다.", example = "true")
                @JsonProperty("isDefault")
                boolean isDefault) {

    public enum MethodType {
        CARD,
        REALTIME_TRANSFER
    }
}
