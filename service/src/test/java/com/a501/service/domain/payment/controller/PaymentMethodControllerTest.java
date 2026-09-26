package com.a501.service.domain.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.account.AccountErrorCode;
import com.a501.service.domain.payment.dto.response.PaymentMethodListResponse;
import com.a501.service.domain.payment.dto.response.PaymentMethodResponse;
import com.a501.service.domain.payment.service.PaymentMethodService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 목 명세 `GET /api/payment-methods` 와 같은 계약인지 봅니다. (R-PAY-09) */
@WebMvcSecurityTest(PaymentMethodController.class)
class PaymentMethodControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private PaymentMethodService paymentMethodService;

    @Test
    void returnsTheIssuedAccountAsTheOnlyMethod() throws Exception {
        when(paymentMethodService.findPaymentMethods(12)).thenReturn(new PaymentMethodListResponse(List.of(
                new PaymentMethodResponse(
                        31, PaymentMethodResponse.MethodType.REALTIME_TRANSFER, "싸피은행 ···· 8842", true))));

        mockMvc.perform(get("/api/payment-methods")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"content":[{"paymentMethodId":31,"methodType":"REALTIME_TRANSFER",
                                     "maskedNumber":"싸피은행 ···· 8842","isDefault":true}]}
                        """, true));
    }

    @Test
    void mapsEscrowFailureToServiceUnavailable() throws Exception {
        when(paymentMethodService.findPaymentMethods(12))
                .thenThrow(new BusinessException(AccountErrorCode.ESCROW_UNAVAILABLE));

        mockMvc.perform(get("/api/payment-methods")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("""
                        {"code":"ESCROW_UNAVAILABLE",
                         "message":"지금은 계좌를 확인할 수 없습니다. 잠시 뒤 다시 시도해 주세요."}
                        """, true));
    }

    @Test
    void rejectsRequestsWithoutATokenBeforeCallingTheService() throws Exception {
        mockMvc.perform(get("/api/payment-methods"))
                .andExpect(status().isUnauthorized());

        verify(paymentMethodService, never()).findPaymentMethods(any());
    }
}
