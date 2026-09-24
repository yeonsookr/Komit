package com.a501.service.domain.diagnosis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static com.a501.service.common.security.SecuritySliceTestSupport.expiredToken;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionListResponse;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionResponse;
import com.a501.service.domain.diagnosis.service.QaSuggestionService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 확인 문장 후보 생성의 계약 테스트. 기대 응답은 목 서버 예시를 옮긴 것입니다. (BACKEND_CONVENTIONS 19) */
@WebMvcSecurityTest(QaSuggestionController.class)
class QaSuggestionControllerTest {
    private static final int USER_ID = 12;
    private static final String PATH = "/api/issues/312/qa-suggestions";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private QaSuggestionService qaSuggestionService;

    @Test
    @DisplayName("후보 세 건을 목 예시와 같은 형식으로 돌려준다")
    void returnsThreeSuggestions() throws Exception {
        when(qaSuggestionService.suggest(312, USER_ID)).thenReturn(QaSuggestionListResponse.of(suggestions()));

        mockMvc.perform(authenticated(post(PATH)))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        """
                        {
                          "content": [
                            {"testCase": "로그인하지 않고 관리자 주소로 들어가면 로그인 화면이 나타난다."},
                            {"testCase": "일반 회원으로 관리자 주소에 들어가면 접근할 수 없다는 안내가 나타난다."},
                            {"testCase": "관리자로 로그인하고 관리자 주소에 들어가면 관리 화면이 열린다."}
                          ],
                          "page": 1,
                          "size": 3,
                          "totalElements": 3,
                          "totalPages": 1
                        }
                        """,
                        true));
    }

    @Test
    @DisplayName("없는 요구사항과 남의 요구사항은 같은 404 ISSUE_NOT_FOUND다")
    void missingOrOthersIssueIsNotFound() throws Exception {
        when(qaSuggestionService.suggest(312, USER_ID))
                .thenThrow(new BusinessException(DiagnosisErrorCode.ISSUE_NOT_FOUND));

        mockMvc.perform(authenticated(post(PATH)))
                .andExpect(status().isNotFound())
                .andExpect(content().json(
                        "{\"code\":\"ISSUE_NOT_FOUND\",\"message\":\"요구사항을 찾을 수 없습니다.\"}", true));
    }

    @Test
    @DisplayName("AI가 계약에 맞는 후보를 못 만들면 502 AI_RESPONSE_INVALID다")
    void invalidAiResponseIsBadGateway() throws Exception {
        when(qaSuggestionService.suggest(312, USER_ID))
                .thenThrow(new BusinessException(DiagnosisErrorCode.AI_RESPONSE_INVALID));

        mockMvc.perform(authenticated(post(PATH)))
                .andExpect(status().isBadGateway())
                .andExpect(content().json(
                        "{\"code\":\"AI_RESPONSE_INVALID\",\"message\":\"확인 문장을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.\"}",
                        true));
    }

    @Test
    @DisplayName("AI에 닿지 못하면 503 AI_SERVICE_UNAVAILABLE다")
    void unreachableAiIsServiceUnavailable() throws Exception {
        when(qaSuggestionService.suggest(312, USER_ID))
                .thenThrow(new BusinessException(DiagnosisErrorCode.AI_SERVICE_UNAVAILABLE));

        mockMvc.perform(authenticated(post(PATH)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json(
                        "{\"code\":\"AI_SERVICE_UNAVAILABLE\",\"message\":\"확인 문장 생성 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.\"}",
                        true));
    }

    @Test
    @DisplayName("식별자가 숫자가 아니면 422이고 서비스는 불리지 않는다")
    void nonNumericIssueIdIsInvalidRequest() throws Exception {
        mockMvc.perform(authenticated(post("/api/issues/abc/qa-suggestions")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("issueId"));

        verify(qaSuggestionService, never()).suggest(any(), any());
    }

    @Test
    @DisplayName("토큰이 없거나 만료되면 401 UNAUTHORIZED이고 서비스는 불리지 않는다")
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(post(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        "{\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}", true));
        mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken(USER_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        "{\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}", true));

        verify(qaSuggestionService, never()).suggest(any(), any());
    }

    @Test
    @DisplayName("AI 가 우리 요청을 거부하면 500 INTERNAL_ERROR다")
    void ourMistakeIsInternalError() throws Exception {
        when(qaSuggestionService.suggest(312, USER_ID))
                .thenThrow(new BusinessException(CommonErrorCode.INTERNAL_ERROR));

        mockMvc.perform(authenticated(post(PATH)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json(
                        "{\"code\":\"INTERNAL_ERROR\",\"message\":\"요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.\"}",
                        true));
    }

    private static List<QaSuggestionResponse> suggestions() {
        return List.of(
                new QaSuggestionResponse("로그인하지 않고 관리자 주소로 들어가면 로그인 화면이 나타난다."),
                new QaSuggestionResponse("일반 회원으로 관리자 주소에 들어가면 접근할 수 없다는 안내가 나타난다."),
                new QaSuggestionResponse("관리자로 로그인하고 관리자 주소에 들어가면 관리 화면이 열린다."));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID));
    }
}
