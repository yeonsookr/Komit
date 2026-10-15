package com.a501.service.common.realtime.ticket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeErrorCode;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcSecurityTest(RealtimeTicketController.class)
class RealtimeTicketControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private RealtimeTicketIssuer ticketIssuer;

    private static final String BODY = "{\"channel\":\"CONSULTING_SESSION\",\"channelId\":\"55\"}";

    @Test
    @DisplayName("로그인하지 않으면 티켓을 받을 수 없다")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/realtime/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        verify(ticketIssuer, never()).issue(any(), any(), any());
    }

    @Test
    @DisplayName("토큰의 사용자로 티켓을 발급한다 — 요청 본문의 사용자를 믿지 않는다")
    void issuesForAuthenticatedUser() throws Exception {
        when(ticketIssuer.issue(eq(12), eq(RealtimeChannelType.CONSULTING_SESSION), eq("55")))
                .thenReturn("t-1");

        mockMvc.perform(post("/api/realtime/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").value("t-1"));
    }

    @Test
    @DisplayName("권한이 없으면 403 CHANNEL_FORBIDDEN 으로 나간다")
    void rejectsUnauthorizedChannel() throws Exception {
        when(ticketIssuer.issue(any(), any(), any()))
                .thenThrow(new BusinessException(RealtimeErrorCode.CHANNEL_FORBIDDEN));

        mockMvc.perform(post("/api/realtime/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHANNEL_FORBIDDEN"));
    }

    @Test
    @DisplayName("모르는 채널이나 빈 식별자는 422 로 거른다")
    void validatesRequest() throws Exception {
        mockMvc.perform(post("/api/realtime/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"CONSULTING_SESSION\",\"channelId\":\"  \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(ticketIssuer, never()).issue(any(), any(), any());
    }

    @Test
    @DisplayName("모르는 채널 값도 계약 오류 형식으로 나간다")
    void rejectsUnknownChannel() throws Exception {
        // channel 이 enum 이라 Jackson 이 역직렬화에서 먼저 막는다. 그 실패도 code 를 갖춘
        // 응답으로 나가야 한다. 1-e 의 소켓 경계에서 같은 번역이 필요하다.
        mockMvc.perform(post("/api/realtime/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"BOGUS\",\"channelId\":\"55\"}"))
                .andExpect(jsonPath("$.code").exists());

        verify(ticketIssuer, never()).issue(any(), any(), any());
    }

    @Test
    @DisplayName("WebSocket 경로는 보안 체인이 401 로 막지 않는다 — 티켓이 관문이다")
    void webSocketPathIsNotBlockedBySecurityChain() throws Exception {
        // 브라우저는 핸드셰이크에 Authorization 헤더를 붙일 수 없다. 여기서 401 이 나면
        // RealtimeHandshakeInterceptor 의 티켓 검사에 닿지도 못한다.
        int status = mockMvc.perform(get("/ws/realtime").param("ticket", "t-1"))
                .andReturn()
                .getResponse()
                .getStatus();

        // 이 슬라이스에는 WebSocket 핸들러가 없어 실제 상태 코드는 의미가 없다.
        // 확인할 것은 하나다 — 보안 체인이 401 로 잘라내지 않는다.
        assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
}
