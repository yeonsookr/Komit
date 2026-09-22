package com.a501.service.domain.diagnosis.controller;

import static com.a501.service.common.security.SecuritySliceTestSupport.expiredToken;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.SurveyAnswerType;
import com.a501.service.domain.diagnosis.dto.request.SaveSurveyAnswersRequest.SurveyAnswerRequest;
import com.a501.service.domain.diagnosis.dto.request.StartDiagnosisRequest;
import com.a501.service.domain.diagnosis.dto.response.DiagnosisProjectResponse;
import com.a501.service.domain.diagnosis.dto.response.DiagnosisResponse;
import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.diagnosis.dto.response.InspectionResponse;
import com.a501.service.domain.diagnosis.dto.response.IssueListResponse;
import com.a501.service.domain.diagnosis.dto.response.IssueResponse;
import com.a501.service.domain.diagnosis.dto.response.SurveyOptionResponse;
import com.a501.service.domain.diagnosis.dto.response.SurveyQuestionResponse;
import com.a501.service.domain.diagnosis.dto.response.SurveyResponse;
import com.a501.service.domain.diagnosis.dto.response.TechCategoryResponse;
import com.a501.service.domain.diagnosis.service.DiagnosisService;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
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

    @Test
    @DisplayName("설문 조회는 문항과 보기를 순서대로 주고 고른 보기를 isSelected로 표시한다")
    void getSurveyReturnsQuestionsInOrder() throws Exception {
        when(diagnosisService.getSurvey(31, USER_ID)).thenReturn(survey(false));

        mockMvc.perform(authenticated(get("/api/diagnoses/31/survey")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"surveyId":19,"diagnosisVersion":2,"isDone":false,
                         "questions":[
                           {"questionId":51,"content":"지금 어디까지 왔나요","answerType":"SINGLE",
                            "options":[{"optionId":101,"content":"내 컴퓨터에서만 돌아갑니다","isSelected":false},
                                       {"optionId":102,"content":"배포는 했는데 아직 아무도 안 씁니다","isSelected":true}]},
                           {"questionId":53,"content":"무슨 일이 있었는지 적어주세요","answerType":"TEXT",
                            "options":[{"optionId":121,"content":"결제 버튼이 안 눌려요","isSelected":true}]}]}
                        """, true));
    }

    @Test
    @DisplayName("설문 답 저장은 저장 뒤의 설문지 전체를 돌려준다")
    void saveSurveyAnswersReturnsWholeSurvey() throws Exception {
        when(diagnosisService.saveSurveyAnswers(
                        31, USER_ID, List.of(new SurveyAnswerRequest(51, List.of(102), null))))
                .thenReturn(survey(false));

        mockMvc.perform(authenticated(patch("/api/diagnoses/31/survey/answers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":51,\"selectedOptionIds\":[102]}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surveyId").value(19))
                .andExpect(jsonPath("$.questions[0].options[1].isSelected").value(true));
    }

    @Test
    @DisplayName("답 저장에 answers가 비어 있으면 422 INVALID_REQUEST다")
    void saveSurveyAnswersRejectsEmptyAnswers() throws Exception {
        mockMvc.perform(authenticated(patch("/api/diagnoses/31/survey/answers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("answers"));

        verify(diagnosisService, never()).saveSurveyAnswers(any(), any(), any());
    }

    @Test
    @DisplayName("설문 완료는 isDone이 참인 설문지를 돌려준다")
    void completeSurveyMarksDone() throws Exception {
        when(diagnosisService.completeSurvey(31, USER_ID)).thenReturn(survey(true));

        mockMvc.perform(authenticated(post("/api/diagnoses/31/survey/complete")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDone").value(true));
    }

    @Test
    @DisplayName("답하지 않은 문항이 남았거나 이미 끝낸 설문은 409 SURVEY_NOT_COMPLETABLE이다")
    void completeSurveyConflicts() throws Exception {
        when(diagnosisService.completeSurvey(409, USER_ID))
                .thenThrow(new BusinessException(DiagnosisErrorCode.SURVEY_NOT_COMPLETABLE));

        mockMvc.perform(authenticated(post("/api/diagnoses/409/survey/complete")))
                .andExpect(status().isConflict())
                .andExpect(content().json(
                        "{\"code\":\"SURVEY_NOT_COMPLETABLE\",\"message\":\"아직 끝내지 않은 문항이 있습니다.\"}", true));
    }

    @Test
    @DisplayName("검사 파일은 본문이 파일 내용이고 파일 이름은 Content-Disposition 에 담긴다")
    void downloadScanFileReturnsMarkdown() throws Exception {
        when(diagnosisService.getScanFile(31, USER_ID))
                .thenReturn("# Komit 검사 파일\n\n- 진단 번호: 31\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(authenticated(get("/api/diagnoses/31/scan-file")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/markdown;charset=UTF-8"))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"komit-scan.md\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("진단 번호: 31")));
    }

    @Test
    @DisplayName("검사 결과 파일을 올리면 201로 검사 한 건을 돌려준다")
    void uploadScanResultReturnsCreated() throws Exception {
        when(diagnosisService.uploadScanResult(eq(31), eq(USER_ID), any()))
                .thenReturn(new InspectionResponse(
                        27, 2, "IN_PROGRESS", 11,
                        OffsetDateTime.of(2026, 8, 17, 0, 12, 0, 0, ZoneOffset.UTC)));

        mockMvc.perform(multipart("/api/diagnoses/31/inspections")
                        .file(new MockMultipartFile(
                                "file", "komit-result.md", "text/markdown", "```json\n{}\n```".getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"inspectionId":27,"diagnosisVersion":2,"status":"IN_PROGRESS",
                         "readinessScore":11,"scannedAt":"2026-08-17T00:12:00Z"}
                        """, true));
    }

    @Test
    @DisplayName("읽을 수 없는 결과 파일은 400 SCAN_FILE_INVALID다")
    void uploadRejectsUnreadableFile() throws Exception {
        when(diagnosisService.uploadScanResult(eq(31), eq(USER_ID), any()))
                .thenThrow(new BusinessException(DiagnosisErrorCode.SCAN_FILE_INVALID));

        mockMvc.perform(multipart("/api/diagnoses/31/inspections")
                        .file(new MockMultipartFile(
                                "file", "komit-result.md", "text/markdown", "hello".getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(
                        "{\"code\":\"SCAN_FILE_INVALID\","
                                + "\"message\":\"검사 결과 파일을 읽지 못했습니다. 받은 파일 그대로 올려주세요.\"}", true));
    }

    @Test
    @DisplayName("다른 진단의 결과 파일은 400 DIAGNOSIS_MISMATCH다")
    void uploadRejectsOtherDiagnosisFile() throws Exception {
        when(diagnosisService.uploadScanResult(eq(31), eq(USER_ID), any()))
                .thenThrow(new BusinessException(DiagnosisErrorCode.DIAGNOSIS_MISMATCH));

        mockMvc.perform(multipart("/api/diagnoses/31/inspections")
                        .file(new MockMultipartFile(
                                "file", "komit-result.md", "text/markdown", "{}".getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DIAGNOSIS_MISMATCH"));
    }

    @Test
    @DisplayName("결함 목록은 content와 페이지 정보로 감싼다")
    void getIssuesReturnsPagedContract() throws Exception {
        when(diagnosisService.getIssues(31, USER_ID, null, null, 1, 20))
                .thenReturn(new IssueListResponse(List.of(issue()), 1, 20, 11, 1));

        mockMvc.perform(authenticated(get("/api/diagnoses/31/issues")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"content":[{"issueId":311,"title":"관리자 API에 인증 추가",
                                     "detail":"로그인한 사람이면 남의 주문 내역까지 열람 가능합니다",
                                     "location":"app/api/checkout/route.ts:47","isInput":false,
                                     "category":{"codeId":71,"codeName":"보안"},
                                     "status":"UNRESOLVED","severity":"HIGH",
                                     "fixedVersion":null,"originIssueId":null,"qaCriteria":[]}],
                         "page":1,"size":20,"totalElements":11,"totalPages":1}
                        """, true));
    }

    @Test
    @DisplayName("거르는 조건과 페이지 값을 그대로 넘긴다")
    void getIssuesPassesFilters() throws Exception {
        when(diagnosisService.getIssues(eq(31), eq(USER_ID), any(), any(), anyInt(), anyInt()))
                .thenReturn(new IssueListResponse(List.of(), 1, 20, 0, 0));

        mockMvc.perform(authenticated(get("/api/diagnoses/31/issues"))
                        .param("status", "RESOLVED")
                        .param("isInput", "true")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(diagnosisService).getIssues(31, USER_ID, "RESOLVED", true, 2, 5);
    }

    @Test
    @DisplayName("허용되지 않은 status 는 422 INVALID_REQUEST다")
    void getIssuesRejectsUnknownStatus() throws Exception {
        mockMvc.perform(authenticated(get("/api/diagnoses/31/issues")).param("status", "NOPE"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("status"));

        verify(diagnosisService, never()).getIssues(any(), any(), any(), any(), anyInt(), anyInt());
    }

    private static IssueResponse issue() {
        return new IssueResponse(
                311,
                "관리자 API에 인증 추가",
                "로그인한 사람이면 남의 주문 내역까지 열람 가능합니다",
                "app/api/checkout/route.ts:47",
                false,
                new CodeResponse(71, "보안"),
                "UNRESOLVED",
                "HIGH",
                null,
                null,
                List.of());
    }

    private static SurveyResponse survey(boolean done) {
        return new SurveyResponse(
                19,
                2,
                List.of(
                        new SurveyQuestionResponse(
                                51,
                                "지금 어디까지 왔나요",
                                SurveyAnswerType.SINGLE,
                                List.of(
                                        new SurveyOptionResponse(101, "내 컴퓨터에서만 돌아갑니다", false),
                                        new SurveyOptionResponse(102, "배포는 했는데 아직 아무도 안 씁니다", true))),
                        new SurveyQuestionResponse(
                                53,
                                "무슨 일이 있었는지 적어주세요",
                                SurveyAnswerType.TEXT,
                                List.of(new SurveyOptionResponse(121, "결제 버튼이 안 눌려요", true)))),
                done);
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
