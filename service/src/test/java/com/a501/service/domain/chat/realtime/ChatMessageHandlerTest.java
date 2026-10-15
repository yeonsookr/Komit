package com.a501.service.domain.chat.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.RealtimePrincipal;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeErrorCode;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;
import com.a501.service.common.realtime.relay.RealtimeRelayPublisher;
import com.a501.service.domain.chat.dto.response.ChatMessageResponse;
import com.a501.service.domain.chat.service.ChatMessageService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 올라온 말을 남기고 방에 넘기는 규칙을 검증합니다. 저장하는 것, 보낸 사람도 받는 것, 빈 말을 거르는 것이 대상입니다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatMessageHandlerTest {
    private static final String CHAT_REF_ID = "room-92-chat";
    private static final Integer CLIENT_ID = 9001;
    private static final String CONNECTION_ID = "conn-1";
    private static final OffsetDateTime SENT_AT =
            OffsetDateTime.of(2026, 9, 23, 14, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private ChatMessageService chatMessageService;
    @Mock
    private RealtimeRelayPublisher relayPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatMessageHandler handler;

    private final RealtimePrincipal principal = new RealtimePrincipal(
            CLIENT_ID, CONNECTION_ID, RealtimeChannelType.CHAT_ROOM, CHAT_REF_ID);

    @BeforeEach
    void setUp() {
        handler = new ChatMessageHandler(chatMessageService, relayPublisher, objectMapper);
        when(chatMessageService.write(any(), any(), any()))
                .thenReturn(new ChatMessageResponse(
                        "65f1c2a48b3e4d0012a7f901", CLIENT_ID, "안녕하세요", SENT_AT));
    }

    @Test
    @DisplayName("CHAT 집안의 CHAT_MESSAGE 를 맡는다")
    void supportsChatMessage() {
        assertThat(handler.family()).isEqualTo(RealtimeEventFamily.CHAT);
        assertThat(handler.supports("CHAT_MESSAGE")).isTrue();
        assertThat(handler.supports("RTC_OFFER")).isFalse();
        assertThat(handler.supports("CHAT_UNKNOWN")).isFalse();
    }

    @Test
    @DisplayName("올라온 말을 남긴다")
    void savesMessage() {
        handler.handle(principal, envelope("{\"content\":\"안녕하세요\"}"));

        verify(chatMessageService).write(CHAT_REF_ID, CLIENT_ID, "안녕하세요");
    }

    @Test
    @DisplayName("보낸 사람을 빼지 않고 방에 넘긴다")
    void relaysToEveryone() {
        handler.handle(principal, envelope("{\"content\":\"안녕하세요\"}"));

        ArgumentCaptor<RealtimeEnvelope> captor = ArgumentCaptor.forClass(RealtimeEnvelope.class);
        // 연결 식별자를 받는 쪽으로 부르면 보낸 사람이 빠진다. 그 길을 쓰지 않는다.
        verify(relayPublisher, never()).publish(any(), any());
        verify(relayPublisher).publish(captor.capture());

        RealtimeEnvelope relayed = captor.getValue();
        assertThat(relayed.type()).isEqualTo("CHAT_MESSAGE");
        assertThat(relayed.channelId()).isEqualTo(CHAT_REF_ID);
        assertThat(relayed.payload().get("messageId").asString())
                .isEqualTo("65f1c2a48b3e4d0012a7f901");
        assertThat(relayed.payload().get("content").asString()).isEqualTo("안녕하세요");
    }

    @Test
    @DisplayName("보낸 요청의 식별자를 그대로 달아 돌려준다")
    void keepsRequestId() {
        handler.handle(principal, envelope("{\"content\":\"안녕하세요\"}"));

        ArgumentCaptor<RealtimeEnvelope> captor = ArgumentCaptor.forClass(RealtimeEnvelope.class);
        verify(relayPublisher).publish(captor.capture());
        // 화면이 「보내는 중」으로 그려 둔 말과 짝을 맞추는 값이다.
        assertThat(captor.getValue().requestId()).isEqualTo("req-1");
    }

    @Test
    @DisplayName("빈 말은 거른다")
    void rejectsBlankContent() {
        assertThatThrownBy(() -> handler.handle(principal, envelope("{\"content\":\"   \"}")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RealtimeErrorCode.MALFORMED_ENVELOPE);
        verifyNothingHappened();
    }

    @Test
    @DisplayName("본문이 없으면 거른다")
    void rejectsMissingContent() {
        assertThatThrownBy(() -> handler.handle(principal, envelope("{}")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RealtimeErrorCode.MALFORMED_ENVELOPE);
        verifyNothingHappened();
    }

    @Test
    @DisplayName("모르는 칸이 붙어 오면 거른다")
    void rejectsExtraFields() {
        assertThatThrownBy(() -> handler.handle(
                        principal, envelope("{\"content\":\"안녕\",\"senderUserId\":9999}")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RealtimeErrorCode.MALFORMED_ENVELOPE);
        verifyNothingHappened();
    }

    @Test
    @DisplayName("보낸 사람은 연결에 적힌 사람이다")
    void trustsConnectionOverPayload() {
        handler.handle(principal, envelope("{\"content\":\"안녕하세요\"}"));

        // 본문에 적어 보낸 값이 아니라 티켓으로 증명된 사람을 쓴다.
        verify(chatMessageService).write(CHAT_REF_ID, CLIENT_ID, "안녕하세요");
    }

    private void verifyNothingHappened() {
        verify(chatMessageService, never()).write(any(), any(), any());
        verify(relayPublisher, never()).publish(any());
    }

    private RealtimeEnvelope envelope(String payloadJson) {
        JsonNode payload = objectMapper.readTree(payloadJson);
        return new RealtimeEnvelope(
                RealtimeEnvelope.CURRENT_VERSION,
                "CHAT_MESSAGE",
                "req-1",
                RealtimeChannelType.CHAT_ROOM,
                CHAT_REF_ID,
                payload);
    }
}
