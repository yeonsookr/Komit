package com.a501.service.common.realtime.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a501.service.common.exception.BusinessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RealtimeEnvelopeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RealtimeEnvelope envelope(Integer version, String type) {
        return new RealtimeEnvelope(
                version, type, "req-1", RealtimeChannelType.CONSULTING_SESSION, "55", payload());
    }

    private JsonNode payload() {
        return MAPPER.createObjectNode().put("negotiationId", "n-1");
    }

    @Test
    void 제대로_된_봉투는_그대로_통과한다() {
        RealtimeEnvelope given = envelope(1, "RTC_SDP_OFFER");

        assertThat(RealtimeEnvelope.validated(given)).isSameAs(given);
    }

    @Test
    void 아는_버전이_아니면_거절한다() {
        assertThatThrownBy(() -> RealtimeEnvelope.validated(envelope(2, "RTC_SDP_OFFER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RealtimeErrorCode.UNSUPPORTED_PROTOCOL_VERSION);
    }

    @Test
    void 필수_값이_비면_거절한다() {
        RealtimeEnvelope noChannelId =
                new RealtimeEnvelope(
                        1, "RTC_SDP_OFFER", "req-1", RealtimeChannelType.CONSULTING_SESSION, " ", payload());

        assertThatThrownBy(() -> RealtimeEnvelope.validated(noChannelId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RealtimeErrorCode.MALFORMED_ENVELOPE);
    }

    @Test
    void 모르는_갈래의_이벤트는_거절한다() {
        assertThatThrownBy(() -> RealtimeEnvelope.validated(envelope(1, "MAIL_SEND")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RealtimeErrorCode.UNKNOWN_EVENT_TYPE);
    }

    @Test
    void 갈래는_이름_앞머리로_가른다() {
        assertThat(RealtimeEventFamily.of("RTC_SDP_OFFER")).isEqualTo(RealtimeEventFamily.RTC);
        assertThat(RealtimeEventFamily.of("CHAT_SEND")).isEqualTo(RealtimeEventFamily.CHAT);
        assertThat(RealtimeEventFamily.of("SYSTEM_PING")).isEqualTo(RealtimeEventFamily.SYSTEM);
        assertThat(RealtimeEventFamily.of("RTC")).isNull();
        assertThat(RealtimeEventFamily.of("_RTC")).isNull();
        assertThat(RealtimeEventFamily.of(null)).isNull();
    }

    @Test
    void 답장은_요청_식별자와_채널을_이어받는다() {
        RealtimeEnvelope given = envelope(1, "RTC_SDP_OFFER");

        RealtimeEnvelope reply = given.reply("SYSTEM_ERROR", null);

        assertThat(reply.requestId()).isEqualTo("req-1");
        assertThat(reply.channel()).isEqualTo(RealtimeChannelType.CONSULTING_SESSION);
        assertThat(reply.channelId()).isEqualTo("55");
        assertThat(reply.version()).isEqualTo(RealtimeEnvelope.CURRENT_VERSION);
    }

    @Test
    void 봉투는_payload_를_해석하지_않고_실어_나른다() throws Exception {
        String json =
                """
                {"version":1,"type":"CHAT_SEND","requestId":"req-2",
                 "channel":"CHAT_ROOM","channelId":"room-92-chat",
                 "payload":{"body":"안녕하세요","clientMessageId":"c-1"}}
                """;

        RealtimeEnvelope parsed = RealtimeEnvelope.validated(MAPPER.readValue(json, RealtimeEnvelope.class));

        assertThat(parsed.family()).isEqualTo(RealtimeEventFamily.CHAT);
        assertThat(parsed.payload().get("body").asText()).isEqualTo("안녕하세요");
    }
}
