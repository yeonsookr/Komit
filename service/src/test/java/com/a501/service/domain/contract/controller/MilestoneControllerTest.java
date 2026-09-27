package com.a501.service.domain.contract.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.InvalidFieldException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.request.ConfirmMilestoneRequest;
import com.a501.service.domain.contract.dto.request.SaveQaResultsRequest;
import com.a501.service.domain.contract.dto.response.MilestoneResponse;
import com.a501.service.domain.contract.service.MilestoneProgressService;
import com.a501.service.domain.diagnosis.dto.response.QaCriterionResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 작업 확인 API의 경로, 상태 코드, 요청 읽기, 오류 본문이 목 명세와 같은지 본다. (R-QA-01~03) */
@WebMvcSecurityTest(MilestoneController.class)
class MilestoneControllerTest {
    private static final int USER_ID = 12;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider tokenProvider;
    @MockitoBean private MilestoneProgressService milestoneProgressService;

    @Test
    void submitsWithoutBodyOrWithEmptyBody() throws Exception {
        when(milestoneProgressService.submit(302, USER_ID)).thenReturn(response("SUBMITTED"));

        mockMvc.perform(authorized(post("/api/milestones/302/submission")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.milestoneId").value(302))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        send(post("/api/milestones/302/submission"), "{}").andExpect(status().isCreated());
    }

    @Test
    void savesCheckedCriteria() throws Exception {
        when(milestoneProgressService.saveQaResults(eq(302), eq(USER_ID), any())).thenReturn(response("IN_PROGRESS"));

        send(put("/api/milestones/302/qa-results"), """
                {"qaCriterionIds":[201]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qaCriteria[0].status").value("RESOLVED"));

        ArgumentCaptor<SaveQaResultsRequest> sent = ArgumentCaptor.forClass(SaveQaResultsRequest.class);
        verify(milestoneProgressService).saveQaResults(eq(302), eq(USER_ID), sent.capture());
        assertThat(sent.getValue().qaCriterionIds()).containsExactly(201);
    }

    @Test
    void rejectsEmptyQaResultsLikeTheMock() throws Exception {
        send(put("/api/milestones/302/qa-results"), """
                {"qaCriterionIds":[]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("qaCriterionIds"))
                .andExpect(jsonPath("$.errors[0].reason").value("1개 이상이어야 합니다."));

        verifyNoInteractions(milestoneProgressService);
    }

    @Test
    void confirmationNeedsIdsButLeavesAnEmptyListToTheService() throws Exception {
        send(post("/api/milestones/302/confirmation"), "{}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("qaCriterionIds"))
                .andExpect(jsonPath("$.errors[0].reason").value("필수 항목입니다."));
        verifyNoInteractions(milestoneProgressService);

        when(milestoneProgressService.confirm(eq(302), eq(USER_ID), any()))
                .thenThrow(new BusinessException(ContractErrorCode.QA_NOT_ALL_CHECKED));
        send(post("/api/milestones/302/confirmation"), """
                {"qaCriterionIds":[]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QA_NOT_ALL_CHECKED"))
                .andExpect(jsonPath("$.message").value("확인 문장을 모두 체크해야 통과시킬 수 있습니다."));
        ArgumentCaptor<ConfirmMilestoneRequest> sent = ArgumentCaptor.forClass(ConfirmMilestoneRequest.class);
        verify(milestoneProgressService).confirm(eq(302), eq(USER_ID), sent.capture());
        assertThat(sent.getValue().qaCriterionIds()).isEmpty();
    }

    @Test
    void confirmsWithCreated() throws Exception {
        when(milestoneProgressService.confirm(eq(302), eq(USER_ID), any())).thenReturn(response("CONFIRMED"));

        send(post("/api/milestones/302/confirmation"), """
                {"qaCriterionIds":[201]}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void reportsCriteriaOfAnotherMilestoneAsAFieldError() throws Exception {
        when(milestoneProgressService.saveQaResults(eq(302), eq(USER_ID), any()))
                .thenThrow(new InvalidFieldException("qaCriterionIds", "이 작업의 확인 문장만 보낼 수 있습니다."));

        send(put("/api/milestones/302/qa-results"), """
                {"qaCriterionIds":[999]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("qaCriterionIds"))
                .andExpect(jsonPath("$.errors[0].reason").value("이 작업의 확인 문장만 보낼 수 있습니다."));
    }

    @Test
    void mapsDomainErrors() throws Exception {
        when(milestoneProgressService.submit(302, USER_ID))
                .thenThrow(new BusinessException(ContractErrorCode.MILESTONE_NOT_FOUND))
                .thenThrow(new BusinessException(ContractErrorCode.MILESTONE_STATE_CONFLICT))
                .thenThrow(new BusinessException(ContractErrorCode.HANDOVER_NOT_OPEN))
                .thenThrow(new BusinessException(ContractErrorCode.HANDOVER_MATERIALS_MISSING));

        send(post("/api/milestones/302/submission"), "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MILESTONE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("작업을 찾을 수 없습니다."));
        send(post("/api/milestones/302/submission"), "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MILESTONE_STATE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("지금 상태에서는 처리할 수 없습니다."));
        send(post("/api/milestones/302/submission"), "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDOVER_NOT_OPEN"));
        send(post("/api/milestones/302/submission"), "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDOVER_MATERIALS_MISSING"))
                .andExpect(jsonPath("$.message").value("인수 시험 자료와 해금 파일을 등록한 뒤에 확인을 요청할 수 있습니다."));
    }

    @Test
    void rejectsNonIntegerMilestoneId() throws Exception {
        send(post("/api/milestones/abc/submission"), "{}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("milestoneId"))
                .andExpect(jsonPath("$.errors[0].reason").value("정수여야 합니다."));

        verifyNoInteractions(milestoneProgressService);
    }

    @Test
    void rejectsWithoutToken() throws Exception {
        mockMvc.perform(post("/api/milestones/302/submission")).andExpect(status().isUnauthorized());

        verifyNoInteractions(milestoneProgressService);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(authorized(request).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID));
    }

    private static MilestoneResponse response(String status) {
        return new MilestoneResponse(302, 313, "2차 · 결제 화면", null, 250000L, false, null, 10, status,
                null, null, null, null,
                List.of(new QaCriterionResponse(201, "결제 버튼을 누르면 완료 화면이 뜬다", null, "RESOLVED", 313)));
    }
}
