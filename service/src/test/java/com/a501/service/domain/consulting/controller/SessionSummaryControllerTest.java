package com.a501.service.domain.consulting.controller;

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
import com.a501.service.domain.consulting.ConsultingErrorCode;
import com.a501.service.domain.consulting.dto.SummaryContent;
import com.a501.service.domain.consulting.dto.response.SessionSummaryResponse;
import com.a501.service.domain.consulting.service.ConsultingSessionService;
import com.a501.service.domain.consulting.service.SessionSummaryQueryService;
import com.a501.service.domain.consulting.service.SummaryChangeService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 세션 요약 조회와 변경 후보 반영. 본문은 목 명세의 예시 모양과 strict 로 맞춘다. (BACKEND_CONVENTIONS 19) */
@WebMvcSecurityTest(ConsultingSessionController.class)
class SessionSummaryControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private ConsultingSessionService sessionService;
    @MockitoBean
    private SessionSummaryQueryService summaryQueryService;
    @MockitoBean
    private SummaryChangeService summaryChangeService;

    @Test
    void 만들어진_요약을_목_명세_모양으로_준다() throws Exception {
        when(summaryQueryService.getSummary(7, 55)).thenReturn(new SessionSummaryResponse(
                55,
                List.of(new SummaryContent.Item("회원탈퇴 데이터를 얼마나 보관할지", "법상 최소 요건과 분쟁 대응 사이에서 정했습니다.")),
                List.of(new SummaryContent.Item("회원탈퇴 데이터는 30일 보관 후 완전 삭제", null)),
                List.of(),
                List.of(new SummaryContent.Change(
                        1, SummaryContent.Kind.UPDATE, 312, "검수 기준 갱신", "30일 보관 뒤 삭제로 고칩니다.", false)),
                OffsetDateTime.parse("2026-08-29T05:35:00Z")));

        mockMvc.perform(get("/api/consulting-sessions/55/summary").header(HttpHeaders.AUTHORIZATION, bearer(7)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"sessionId": 55,
                         "discussed": [{"title": "회원탈퇴 데이터를 얼마나 보관할지",
                                        "detail": "법상 최소 요건과 분쟁 대응 사이에서 정했습니다."}],
                         "decided": [{"title": "회원탈퇴 데이터는 30일 보관 후 완전 삭제", "detail": null}],
                         "nextActions": [],
                         "changes": [{"changeId": 1, "kind": "UPDATE", "issueId": 312, "title": "검수 기준 갱신",
                                      "detail": "30일 보관 뒤 삭제로 고칩니다.", "isApplied": false}],
                         "updatedAt": "2026-08-29T05:35:00Z"}""", true));
    }

    @Test
    void 아직_없으면_409() throws Exception {
        when(summaryQueryService.getSummary(7, 55))
                .thenThrow(new BusinessException(ConsultingErrorCode.SUMMARY_NOT_READY));

        mockMvc.perform(get("/api/consulting-sessions/55/summary").header(HttpHeaders.AUTHORIZATION, bearer(7)))
                .andExpect(status().isConflict())
                .andExpect(content().json(
                        "{\"code\": \"SUMMARY_NOT_READY\", \"message\": \"요약을 아직 만드는 중입니다.\"}", true));
    }

    @Test
    void 당사자가_아니면_404() throws Exception {
        when(summaryQueryService.getSummary(99, 55))
                .thenThrow(new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND));

        mockMvc.perform(get("/api/consulting-sessions/55/summary").header(HttpHeaders.AUTHORIZATION, bearer(99)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/api/consulting-sessions/55/summary"))
                .andExpect(status().isUnauthorized());

        verify(summaryQueryService, never()).getSummary(any(), any());
    }

    @Test
    void 고른_후보를_반영하면_201과_바뀐_요약을_준다() throws Exception {
        when(summaryChangeService.apply(7, 55, List.of(1))).thenReturn(new SessionSummaryResponse(
                55,
                List.of(),
                List.of(),
                List.of(),
                List.of(new SummaryContent.Change(
                        1, SummaryContent.Kind.ADD, null, "결제 실패 로그 수집", null, true)),
                OffsetDateTime.parse("2026-09-27T09:00:00Z")));

        mockMvc.perform(post("/api/consulting-sessions/55/summary/applied-changes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(7))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeIds\": [1]}"))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"sessionId": 55, "discussed": [], "decided": [], "nextActions": [],
                         "changes": [{"changeId": 1, "kind": "ADD", "issueId": null, "title": "결제 실패 로그 수집",
                                      "detail": null, "isApplied": true}],
                         "updatedAt": "2026-09-27T09:00:00Z"}""", true));
    }

    @Test
    void 고른_후보가_없으면_422() throws Exception {
        mockMvc.perform(post("/api/consulting-sessions/55/summary/applied-changes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(7))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeIds\": []}"))
                .andExpect(status().isUnprocessableEntity());

        verify(summaryChangeService, never()).apply(any(), any(), any());
    }

    @Test
    void 반영도_로그인하지_않으면_401() throws Exception {
        mockMvc.perform(post("/api/consulting-sessions/55/summary/applied-changes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeIds\": [1]}"))
                .andExpect(status().isUnauthorized());

        verify(summaryChangeService, never()).apply(any(), any(), any());
    }

    private String bearer(int userId) {
        return "Bearer " + tokenProvider.issue(userId);
    }
}
