package com.a501.service.domain.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.chat.ChatErrorCode;
import com.a501.service.domain.chat.dto.response.ChatMessageListResponse;
import com.a501.service.domain.chat.dto.response.ChatMessageResponse;
import com.a501.service.domain.chat.service.ChatMessageService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP 경계를 봅니다. 파라미터가 묶이는 모양, 기본값, 인증, 404 가 대상입니다. */
@WebMvcSecurityTest(ChatController.class)
class ChatControllerTest {
    private static final int USER_ID = 9001;
    private static final int ROOM_ID = 92;
    private static final String ROOM_CHAT = "room-92-chat";
    private static final OffsetDateTime SENT_AT =
            OffsetDateTime.of(2026, 9, 23, 14, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private ChatMessageService chatMessageService;

    @Test
    void returnsMessagesOldestFirst() throws Exception {
        when(chatMessageService.getMessages(eq(ROOM_CHAT), eq(USER_ID), any(), anyInt()))
                .thenReturn(new ChatMessageListResponse(
                        List.of(
                                new ChatMessageResponse(
                                        "65f1c2a48b3e4d0012a7f901", 9001, "결제가 안 붙었다고 하셨죠", SENT_AT),
                                new ChatMessageResponse(
                                        "65f1c2a48b3e4d0012a7f902",
                                        9101,
                                        "네 맞습니다",
                                        SENT_AT.plusMinutes(1))),
                        SENT_AT));

        mockMvc.perform(get("/api/rooms/{roomId}/messages", ROOM_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"content":[
                           {"messageId":"65f1c2a48b3e4d0012a7f901","senderUserId":9001,
                            "content":"결제가 안 붙었다고 하셨죠","sentAt":"2026-09-23T14:00:00Z"},
                           {"messageId":"65f1c2a48b3e4d0012a7f902","senderUserId":9101,
                            "content":"네 맞습니다","sentAt":"2026-09-23T14:01:00Z"}],
                         "nextBefore":"2026-09-23T14:00:00Z"}"""));
    }

    @Test
    void usesThirtyWhenSizeIsOmitted() throws Exception {
        when(chatMessageService.getMessages(eq(ROOM_CHAT), eq(USER_ID), any(), anyInt()))
                .thenReturn(new ChatMessageListResponse(List.of(), null));

        mockMvc.perform(get("/api/rooms/{roomId}/messages", ROOM_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isOk());

        verify(chatMessageService).getMessages(ROOM_CHAT, USER_ID, null, 30);
    }

    @Test
    void bindsTheBeforeCursorAsAnInstantOnTheWire() throws Exception {
        when(chatMessageService.getMessages(eq(ROOM_CHAT), eq(USER_ID), any(), anyInt()))
                .thenReturn(new ChatMessageListResponse(List.of(), null));

        // 직전 응답의 nextBefore 를 그대로 되돌려 보내는 흐름이다.
        mockMvc.perform(get("/api/rooms/{roomId}/messages", ROOM_ID)
                        .param("before", "2026-09-23T14:00:00Z")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isOk());

        ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(chatMessageService)
                .getMessages(eq(ROOM_CHAT), eq(USER_ID), captor.capture(), eq(10));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().toInstant())
                .isEqualTo(SENT_AT.toInstant());
    }

    @Test
    void keepsTheSameInstantWhenTheCursorCarriesAnotherOffset() throws Exception {
        when(chatMessageService.getMessages(eq(ROOM_CHAT), eq(USER_ID), any(), anyInt()))
                .thenReturn(new ChatMessageListResponse(List.of(), null));

        // 같은 시점을 +09:00 으로 적어 보내도 가리키는 순간은 같아야 한다.
        mockMvc.perform(get("/api/rooms/{roomId}/messages", ROOM_ID)
                        .param("before", "2026-09-23T23:00:00+09:00")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isOk());

        ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(chatMessageService)
                .getMessages(eq(ROOM_CHAT), eq(USER_ID), captor.capture(), anyInt());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().toInstant())
                .isEqualTo(SENT_AT.toInstant());
    }

    @Test
    void refusesWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/messages", ROOM_ID))
                .andExpect(status().isUnauthorized());

        verify(chatMessageService, never()).getMessages(any(), any(), any(), anyInt());
    }

    @Test
    void hidesRoomsTheCallerIsNotIn() throws Exception {
        when(chatMessageService.getMessages(eq(ROOM_CHAT), eq(USER_ID), any(), anyInt()))
                .thenThrow(new BusinessException(ChatErrorCode.CHAT_NOT_FOUND));

        mockMvc.perform(get("/api/rooms/{roomId}/messages", ROOM_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_FOUND"));
    }

    @Test
    void readsSessionChatFromItsOwnPath() throws Exception {
        when(chatMessageService.getMessages(eq("session-31-chat"), eq(USER_ID), any(), anyInt()))
                .thenReturn(new ChatMessageListResponse(List.of(), null));

        // 세션 대화는 방 대화와 따로 쌓인다. 이름표가 갈리므로 섞이지 않는다.
        mockMvc.perform(get("/api/consulting-sessions/{sessionId}/messages", 31)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isOk());

        verify(chatMessageService).getMessages("session-31-chat", USER_ID, null, 30);
    }

    @Test
    void refusesACursorThatIsNotATime() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/messages", ROOM_ID)
                        .param("before", "어제")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isUnprocessableEntity());

        verify(chatMessageService, never()).getMessages(any(), any(), any(), anyInt());
    }
}
