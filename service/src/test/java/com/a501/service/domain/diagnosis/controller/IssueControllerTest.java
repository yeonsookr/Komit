package com.a501.service.domain.diagnosis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.dto.request.UpdateIssueStatusRequest;
import com.a501.service.domain.diagnosis.dto.response.IssueResponse;
import com.a501.service.domain.diagnosis.service.IssueQueryService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 요구사항 상태 변경의 계약 테스트. 기대 응답은 목 서버 예시를 옮긴 것입니다. (BACKEND_CONVENTIONS 19) */
@WebMvcSecurityTest(IssueController.class)
class IssueControllerTest {
    private static final int USER_ID = 12;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private IssueQueryService issueQueryService;

    @Test
    @DisplayName("해결로 내리면 상태와 사라진 회차가 함께 담긴다")
    void resolveReturnsUpdatedIssue() throws Exception {
        when(issueQueryService.updateStatus(314, USER_ID, UpdateIssueStatusRequest.Status.RESOLVED))
                .thenReturn(issue("RESOLVED", 2));

        mockMvc.perform(authenticated(patch("/api/issues/314"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.fixedVersion").value(2));
    }

    @Test
    @DisplayName("되돌리면 사라진 회차도 비워진다")
    void reopenClearsFixedVersion() throws Exception {
        when(issueQueryService.updateStatus(314, USER_ID, UpdateIssueStatusRequest.Status.UNRESOLVED))
                .thenReturn(issue("UNRESOLVED", null));

        mockMvc.perform(authenticated(patch("/api/issues/314"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNRESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNRESOLVED"))
                .andExpect(jsonPath("$.fixedVersion").doesNotExist());
    }

    @Test
    @DisplayName("없는 요구사항과 남의 요구사항은 같은 404 ISSUE_NOT_FOUND다")
    void missingOrOthersIssueIsNotFound() throws Exception {
        when(issueQueryService.updateStatus(any(), any(), any()))
                .thenThrow(new BusinessException(DiagnosisErrorCode.ISSUE_NOT_FOUND));

        mockMvc.perform(authenticated(patch("/api/issues/404"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().json(
                        "{\"code\":\"ISSUE_NOT_FOUND\",\"message\":\"요구사항을 찾을 수 없습니다.\"}", true));
    }

    @Test
    @DisplayName("계약에 들어간 요구사항은 409 ISSUE_STATUS_CONFLICT다")
    void contractedIssueConflicts() throws Exception {
        when(issueQueryService.updateStatus(any(), any(), any()))
                .thenThrow(new BusinessException(DiagnosisErrorCode.ISSUE_STATUS_CONFLICT));

        mockMvc.perform(authenticated(patch("/api/issues/409"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ISSUE_STATUS_CONFLICT"));
    }

    @Test
    @DisplayName("RESOLVED도 UNRESOLVED도 아닌 status 는 422다")
    void unknownStatusIsInvalidRequest() throws Exception {
        mockMvc.perform(authenticated(patch("/api/issues/314"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONTRACTED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(issueQueryService, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("토큰이 없으면 401이고 서비스는 불리지 않는다")
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(patch("/api/issues/314")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isUnauthorized());

        verify(issueQueryService, never()).updateStatus(any(), any(), any());
    }

    private static IssueResponse issue(String status, Integer fixedVersion) {
        return new IssueResponse(
                314,
                "관리자 API에 인증 추가",
                null,
                "app/api/admin/users/route.ts:3",
                false,
                new CodeResponse(71, "보안"),
                status,
                "HIGH",
                fixedVersion,
                null,
                List.of());
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID));
    }
}
