package com.a501.service.domain.payment.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.payment.dto.response.PaymentMethodListResponse;
import com.a501.service.domain.payment.service.PaymentMethodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment", description = "예치 결제 수단")
@RestController
@RequestMapping("/api/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {
    private final PaymentMethodService service;

    @Operation(summary = "결제 수단 조회",
            description = "회원가입 때 발급한 계좌를 유일한 예치 출금 수단으로 내려준다. 아직 없으면 발급한다. (R-PAY-09)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "발급 계좌 한 건"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "503", description = "ESCROW_UNAVAILABLE")
    })
    @GetMapping
    PaymentMethodListResponse findPaymentMethods(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.findPaymentMethods(user.userId());
    }
}
