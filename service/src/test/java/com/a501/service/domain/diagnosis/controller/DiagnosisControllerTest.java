package com.a501.service.domain.diagnosis.controller;

import static com.a501.service.common.security.SecuritySliceTestSupport.expiredToken;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.dto.request.StartDiagnosisRequest;
import com.a501.service.domain.diagnosis.dto.response.DiagnosisProjectResponse;
import com.a501.service.domain.diagnosis.dto.response.DiagnosisResponse;
import com.a501.service.domain.diagnosis.dto.response.TechCategoryResponse;
import com.a501.service.domain.diagnosis.service.DiagnosisService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 사전점검 진단 API 의 계약 테스트. 기대 JSON 은 목 서버({@code mock/app/routers/service.py})의 예시를 옮긴 것입니다. (BACKEND_CONVENTIONS 19)
 */
@WebMvcSecurityTest(DiagnosisController.class)
class DiagnosisControllerTest {
    private static final int USER_ID = 12;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private DiagnosisService diagnosisService;

    @Test
    @DisplayName("진단 요약은 래퍼 없이 리소스를 그대로 돌려준다")
    void getDiagnosisReturnsResourceAsIs() throws Exception {
        when(diagnosisService.getDiagnosis(31, USER_ID)).thenReturn(scanned());

        mockMvc.perform(authenticated(get("/api/diagnoses/31")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"diagnosisId":31,"version":2,
                         "project":{"projectId":8,"name":"수제 비누 가게",
                                    "description":"수제 비누를 파는 작은 온라인 가게","domain":null,
                                    "techCategories":[{"techCodeId":21,"codeName":"Next.js"}]},
                         "userInput":"결제 버튼을 누르면 가끔 아무 반응이 없어요",
                         "readinessScore":37,"scannedAt":"2026-08-17T00:12:00Z",
                         "unresolvedIssueCount":8,"totalIssueCount":9,"resolvedSinceLastVersionCount":1}
                        """, true));
    }

    @Test
    @DisplayName("없는 진단과 남의 진단은 같은 404 DIAGNOSIS_NOT_FOUND다")
    void missingOrOthersDiagnosisIsNotFound() throws Exception {
        when(diagnosisService.getDiagnosis(404, USER_ID))
                .thenThrow(new BusinessException(DiagnosisErrorCode.DIAGNOSIS_NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/diagnoses/404")))
                .andExpect(status().isNotFound())
                .andExpect(content().json(
                        "{\"code\":\"DIAGNOSIS_NOT_FOUND\",\"message\":\"진단을 찾을 수 없습니다.\"}", true));
    }

    @Test
    @DisplayName("diagnosisId가 정수가 아니면 422 INVALID_REQUEST다")
    void nonIntegerDiagnosisIdIsInvalidRequest() throws Exception {
        mockMvc.perform(authenticated(get("/api/diagnoses/abc")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json(
                        "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 형식이 올바르지 않습니다.\","
                                + "\"errors\":[{\"field\":\"diagnosisId\",\"reason\":\"정수여야 합니다.\"}]}", true));

        verify(diagnosisService, never()).getDiagnosis(any(), any());
    }

    @Test
    @DisplayName("진단 시작은 201로 답하고 검사 전이라 점수와 시각이 null이다")
    void startDiagnosisReturnsCreated() throws Exception {
        when(diagnosisService.start(new StartDiagnosisRequest(null, "수제 비누 가게", null), USER_ID))
                .thenReturn(justStarted());

        mockMvc.perform(authenticated(post("/api/diagnoses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"수제 비누 가게\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"diagnosisId":32,"version":1,
                         "project":{"projectId":8,"name":"수제 비누 가게","description":null,
                                    "domain":null,"techCategories":[]},
                         "userInput":null,"readinessScore":null,"scannedAt":null,
                         "unresolvedIssueCount":0,"totalIssueCount":0,"resolvedSinceLastVersionCount":0}
                        """, true));
    }

    @Test
    @DisplayName("projectId도 name도 없으면 422 INVALID_REQUEST이고 사유가 name에 달린다")
    void startWithoutProjectIdAndNameIsInvalidRequest() throws Exception {
        mockMvc.perform(authenticated(post("/api/diagnoses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json(
                        "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 형식이 올바르지 않습니다.\","
                                + "\"errors\":[{\"field\":\"name\",\"reason\":\"필수 항목입니다.\"}]}", true));

        verify(diagnosisService, never()).start(any(), anyInt());
    }

    @Test
    @DisplayName("토큰이 없거나 만료되면 401이고 서비스는 불리지 않는다")
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/diagnoses/31"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        "{\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}", true));
        mockMvc.perform(get("/api/diagnoses/31")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken(USER_ID)))
                .andExpect(status().isUnauthorized());

        verify(diagnosisService, never()).getDiagnosis(any(), any());
    }

    private static DiagnosisResponse scanned() {
        return new DiagnosisResponse(
                31,
                2,
                new DiagnosisProjectResponse(
                        8,
                        "수제 비누 가게",
                        "수제 비누를 파는 작은 온라인 가게",
                        null,
                        List.of(new TechCategoryResponse(21, "Next.js"))),
                "결제 버튼을 누르면 가끔 아무 반응이 없어요",
                37,
                OffsetDateTime.of(2026, 8, 17, 0, 12, 0, 0, ZoneOffset.UTC),
                8,
                9,
                1);
    }

    private static DiagnosisResponse justStarted() {
        return new DiagnosisResponse(
                32,
                1,
                new DiagnosisProjectResponse(8, "수제 비누 가게", null, null, List.of()),
                null,
                null,
                null,
                0,
                0,
                0);
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID));
    }
}
