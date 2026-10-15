package com.a501.service.domain.setting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.setting.dto.response.RequiredTermsResponse;
import com.a501.service.domain.setting.service.TermsAgreementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcSecurityTest(TermsAgreementController.class)
class TermsAgreementControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private TermsAgreementService termsAgreementService;

    @Test
    void getReturnsStoredConsentValues() throws Exception {
        when(termsAgreementService.getRequiredTerms(12))
                .thenReturn(new RequiredTermsResponse(false, false, false, false));

        mockMvc.perform(get("/api/settings/terms")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"isEscrowTermsAgreed":false,"isPrivacyCollectAgreed":false,
                         "isPrivacyThirdPartyAgreed":false,"isServiceTermsAgreed":false}
                        """, true));
    }

    @Test
    void putRecordsOnlyCompleteExplicitAgreement() throws Exception {
        when(termsAgreementService.agreeRequiredTerms(12))
                .thenReturn(new RequiredTermsResponse(true, true, true, true));

        mockMvc.perform(put("/api/settings/terms")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isEscrowTermsAgreed":true,"isPrivacyCollectAgreed":true,
                                 "isPrivacyThirdPartyAgreed":true,"isServiceTermsAgreed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"isEscrowTermsAgreed":true,"isPrivacyCollectAgreed":true,
                         "isPrivacyThirdPartyAgreed":true,"isServiceTermsAgreed":true}
                        """, true));

        verify(termsAgreementService).agreeRequiredTerms(12);
    }

    @Test
    void falseAgreementIsRejectedBeforeSaving() throws Exception {
        mockMvc.perform(put("/api/settings/terms")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isEscrowTermsAgreed":false,"isPrivacyCollectAgreed":true,
                                 "isPrivacyThirdPartyAgreed":true,"isServiceTermsAgreed":true}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"isEscrowTermsAgreed","reason":"동의가 필요합니다."}]}
                        """, true));

        verify(termsAgreementService, never()).agreeRequiredTerms(any());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/settings/terms"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}
                        """, true));

        verify(termsAgreementService, never()).getRequiredTerms(any());
    }
}
