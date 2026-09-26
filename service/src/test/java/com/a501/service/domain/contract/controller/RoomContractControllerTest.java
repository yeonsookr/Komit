package com.a501.service.domain.contract.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.request.ContractQaSuggestionRequest;
import com.a501.service.domain.contract.dto.request.WriteDevelopmentContractRequest;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.contract.service.ConsultingContractWriteService;
import com.a501.service.domain.contract.service.ContractDraftService;
import com.a501.service.domain.contract.service.ContractQaSuggestionService;
import com.a501.service.domain.contract.service.DevelopmentContractWriteService;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionListResponse;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcSecurityTest(RoomContractController.class)
class RoomContractControllerTest {
    private static final int USER_ID = 12;
    private static final int ROOM_ID = 92;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider tokenProvider;
    @MockitoBean private ConsultingContractWriteService consultingContractWriteService;
    @MockitoBean private ContractDraftService contractDraftService;
    @MockitoBean private DevelopmentContractWriteService developmentContractWriteService;
    @MockitoBean private ContractQaSuggestionService contractQaSuggestionService;

    @Test
    void generatesDraftForAuthenticatedUser() throws Exception {
        ContractResponse response = new ContractResponse(
                120, ROOM_ID, "CONSULTING", "NEGOTIATING",
                null, null, null, null, List.of(), null, null);
        when(contractDraftService.generate(ROOM_ID, USER_ID)).thenReturn(response);

        mockMvc.perform(post("/api/rooms/{roomId}/contract-draft", ROOM_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value(120))
                .andExpect(jsonPath("$.status").value("NEGOTIATING"));

        verify(contractDraftService).generate(ROOM_ID, USER_ID);
    }

    @Test
    void rejectsDraftWithoutToken() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/contract-draft", ROOM_ID))
                .andExpect(status().isUnauthorized());

        verify(contractDraftService, never()).generate(anyInt(), anyInt());
    }

    @Test
    void rejectsConsultingMessageLongerThanDatabaseColumn() throws Exception {
        String body = """
                {"topic":"회원탈퇴 흐름 확인",
                 "scheduledAt":"2026-12-29T14:00:00+09:00",
                 "durationMinutes":30,
                 "price":50000,
                 "message":"%s"}
                """.formatted("가".repeat(301));

        mockMvc.perform(post("/api/rooms/{roomId}/consulting-contracts", ROOM_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("message"));

        verifyNoInteractions(consultingContractWriteService);
    }

    private static final String DEVELOPMENT_BODY = """
            {"startDate":"2026-08-26","dueDate":"2026-09-16","freeRepairDays":14,
             "milestones":[
               {"issueId":312,"price":400000,"workDate":10,"qaCriteria":["로그인 화면이 뜬다"]},
               {"title":"관리자 화면에 검색 추가","price":200000,"qaCriteria":["검색이 된다"]}],
             "deliverableTitles":["소스 파일 전체"],"message":"잘 부탁드립니다."}
            """;

    @Test
    void writesDevelopmentContract() throws Exception {
        ContractResponse response = new ContractResponse(
                130, 93, "DEVELOPMENT", "NEGOTIATING",
                null, null, null, null, List.of(), null, null);
        when(developmentContractWriteService.write(eq(93), eq(USER_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/rooms/{roomId}/development-contracts", 93)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEVELOPMENT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contractId").value(130))
                .andExpect(jsonPath("$.status").value("NEGOTIATING"));

        ArgumentCaptor<WriteDevelopmentContractRequest> sent =
                ArgumentCaptor.forClass(WriteDevelopmentContractRequest.class);
        verify(developmentContractWriteService).write(eq(93), eq(USER_ID), sent.capture());
        org.assertj.core.api.Assertions.assertThat(sent.getValue().milestones())
                .extracting(milestone -> milestone.issueId())
                .containsExactly(312, null);
    }

    @Test
    void rejectsNewWorkWithoutTitleAsFieldError() throws Exception {
        String body = """
                {"startDate":"2026-08-26","dueDate":"2026-09-16","freeRepairDays":14,
                 "milestones":[{"price":200000,"qaCriteria":["검색이 된다"]}]}
                """;

        mockMvc.perform(post("/api/rooms/{roomId}/development-contracts", 93)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("milestones.0.title"))
                .andExpect(jsonPath("$.errors[0].reason").value("필수 항목입니다."));

        verifyNoInteractions(developmentContractWriteService);
    }

    @Test
    void rejectsDuplicateIssueAsFieldError() throws Exception {
        String body = """
                {"startDate":"2026-08-26","dueDate":"2026-09-16","freeRepairDays":14,
                 "milestones":[
                   {"issueId":312,"price":1,"qaCriteria":["a"]},
                   {"issueId":312,"price":1,"qaCriteria":["b"]}]}
                """;

        mockMvc.perform(post("/api/rooms/{roomId}/development-contracts", 93)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("milestones"))
                .andExpect(jsonPath("$.errors[0].reason").value("같은 요구사항을 두 작업에 담을 수 없습니다."));
    }

    @Test
    void rejectsEmptyQaCriterionWithListIndex() throws Exception {
        String body = """
                {"startDate":"2026-08-26","dueDate":"2026-09-16","freeRepairDays":14,
                 "milestones":[{"issueId":312,"price":1,"qaCriteria":[""]}]}
                """;

        mockMvc.perform(post("/api/rooms/{roomId}/development-contracts", 93)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("milestones.0.qaCriteria.0"))
                .andExpect(jsonPath("$.errors[0].reason").value("1자 이상이어야 합니다."));
    }

    @Test
    void mapsDueDateBeforeStartToBadRequest() throws Exception {
        when(developmentContractWriteService.write(eq(93), eq(USER_ID), any()))
                .thenThrow(new BusinessException(ContractErrorCode.DUE_DATE_BEFORE_START));

        mockMvc.perform(post("/api/rooms/{roomId}/development-contracts", 93)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEVELOPMENT_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUE_DATE_BEFORE_START"))
                .andExpect(jsonPath("$.message").value("납기일은 착수일보다 늦어야 합니다."));
    }

    @Test
    void mapsNonDevelopmentRoomToConflict() throws Exception {
        when(developmentContractWriteService.write(eq(93), eq(USER_ID), any()))
                .thenThrow(new BusinessException(ContractErrorCode.NOT_DEVELOPMENT_ROOM));

        mockMvc.perform(post("/api/rooms/{roomId}/development-contracts", 93)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEVELOPMENT_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_DEVELOPMENT_ROOM"))
                .andExpect(jsonPath("$.message").value("개발방에서만 개발 계약서를 쓸 수 있습니다."));
    }

    @Test
    void rejectsDevelopmentContractWithoutToken() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/development-contracts", 93)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEVELOPMENT_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(developmentContractWriteService);
    }

    @Test
    void suggestsContractQaCriteriaForUnsavedWork() throws Exception {
        when(contractQaSuggestionService.suggest(eq(93), eq(USER_ID), any()))
                .thenReturn(QaSuggestionListResponse.of(List.of(
                        new QaSuggestionResponse("로그인하지 않으면 로그인 화면이 나타난다."),
                        new QaSuggestionResponse("일반 회원은 관리자 화면에 들어갈 수 없다."),
                        new QaSuggestionResponse("관리자는 관리자 화면을 볼 수 있다."))));

        mockMvc.perform(post("/api/rooms/{roomId}/contract-qa-suggestions", 93)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"issueId":null,"title":"관리자 API에 인증 추가","detail":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(3));

        ArgumentCaptor<ContractQaSuggestionRequest> sent =
                ArgumentCaptor.forClass(ContractQaSuggestionRequest.class);
        verify(contractQaSuggestionService).suggest(eq(93), eq(USER_ID), sent.capture());
        org.assertj.core.api.Assertions.assertThat(sent.getValue().issueId()).isNull();
        org.assertj.core.api.Assertions.assertThat(sent.getValue().title())
                .isEqualTo("관리자 API에 인증 추가");
    }

    @Test
    void rejectsInvalidContractQaTextAndMissingToken() throws Exception {
        String body = "{\"issueId\":312,\"title\":\"\",\"detail\":null}";

        mockMvc.perform(post("/api/rooms/{roomId}/contract-qa-suggestions", 93)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        mockMvc.perform(post("/api/rooms/{roomId}/contract-qa-suggestions", 93)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"issueId\":null,\"title\":\"새 작업\",\"detail\":null}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(contractQaSuggestionService);
    }

    @Test
    void rejectsIssueOutsideRequestAndContractWithoutFieldErrors() throws Exception {
        when(contractQaSuggestionService.suggest(eq(93), eq(USER_ID), any()))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_REQUEST));

        mockMvc.perform(post("/api/rooms/{roomId}/contract-qa-suggestions", 93)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"issueId\":999,\"title\":\"남의 작업\",\"detail\":null}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
