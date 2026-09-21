package com.a501.service.domain.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.account.AccountErrorCode;
import com.a501.service.domain.account.dto.response.MemberAccountResponse;
import com.a501.service.domain.account.service.MemberAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 목 명세 `POST /api/accounts/member-account` 와 같은 계약인지 봅니다. (R-ACC-14) */
@WebMvcSecurityTest(MemberAccountController.class)
class MemberAccountControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private MemberAccountService memberAccountService;

    @Test
    void returnsTheIssuedAccountOfTheTokenOwner() throws Exception {
        when(memberAccountService.issue(12))
                .thenReturn(new MemberAccountResponse("001", "0011012345678842"));

        mockMvc.perform(post("/api/accounts/member-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"bankCode":"001","accountNumber":"0011012345678842"}
                        """, true));
    }

    @Test
    void mapsEscrowFailureToServiceUnavailable() throws Exception {
        when(memberAccountService.issue(12))
                .thenThrow(new BusinessException(AccountErrorCode.ESCROW_UNAVAILABLE));

        mockMvc.perform(post("/api/accounts/member-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("""
                        {"code":"ESCROW_UNAVAILABLE",
                         "message":"지금은 계좌를 확인할 수 없습니다. 잠시 뒤 다시 시도해 주세요."}
                        """, true));
    }

    @Test
    void rejectsRequestsWithoutATokenBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/accounts/member-account"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}
                        """, true));

        verify(memberAccountService, never()).issue(any());
    }

    /** 대상 회원을 요청에 담지 않으므로 GET 같은 다른 방법으로 남의 계좌를 부를 자리가 없습니다. */
    @Test
    void doesNotServeTheAccountOverGet() throws Exception {
        mockMvc.perform(get("/api/accounts/member-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12)))
                .andExpect(status().isMethodNotAllowed());

        verify(memberAccountService, never()).issue(any());
    }
}
