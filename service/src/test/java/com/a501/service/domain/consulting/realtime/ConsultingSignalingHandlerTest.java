package com.a501.service.domain.consulting.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.RealtimePrincipal;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeErrorCode;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;
import com.a501.service.common.realtime.relay.RealtimeRelayPublisher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ConsultingSignalingHandlerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RealtimeRelayPublisher relayPublisher = mock(RealtimeRelayPublisher.class);
    private final ConsultingSignalingHandler handler = new ConsultingSignalingHandler(relayPublisher);

    private static final RealtimePrincipal SENDER =
            new RealtimePrincipal(7, "conn-1", RealtimeChannelType.CONSULTING_SESSION, "42");

    @Test
    void 맡은_계열은_통화다() {
        assertThat(handler.family()).isEqualTo(RealtimeEventFamily.RTC);
    }

    @Test
    void 아는_쪽지만_받는다() {
        assertThat(handler.supports("RTC_OFFER")).isTrue();
        assertThat(handler.supports("RTC_ANSWER")).isTrue();
        assertThat(handler.supports("RTC_ICE_CANDIDATE")).isTrue();
        assertThat(handler.supports("RTC_ICE_RESTART")).isTrue();
        assertThat(handler.supports("RTC_PEER_READY")).isTrue();
        assertThat(handler.supports("RTC_PEER_LEFT")).isTrue();
    }

    @Test
    void 모르는_쪽지는_받지_않는다() {
        assertThat(handler.supports("RTC_SOMETHING_ELSE")).isFalse();
        assertThat(handler.supports("CHAT_MESSAGE")).isFalse();
    }

    /** 보낸 사람에게 되돌아가면 같은 pod 에 있는 사람만 두 번 받는다. */
    @Test
    void 보낸_연결을_빼고_중계한다() {
        RealtimeEnvelope envelope =
                envelope(
                        "RTC_OFFER",
                        MAPPER.createObjectNode()
                                .put("negotiationId", "negotiation-1")
                                .set("description", MAPPER.createObjectNode().put("type", "offer")));

        handler.handle(SENDER, envelope);

        verify(relayPublisher).publish(envelope, "conn-1");
    }

    @Test
    void 모든_이벤트의_정상_payload를_중계한다() {
        JsonNode negotiationOnly =
                MAPPER.createObjectNode().put("negotiationId", "negotiation-1");
        JsonNode answer =
                MAPPER.createObjectNode()
                        .put("negotiationId", "negotiation-1")
                        .set("description", MAPPER.createObjectNode().put("type", "answer"));
        JsonNode candidate =
                MAPPER.createObjectNode()
                        .put("negotiationId", "negotiation-1")
                        .set("candidate", MAPPER.createObjectNode().put("candidate", "candidate:1"));

        handler.handle(SENDER, envelope("RTC_PEER_READY", negotiationOnly));
        handler.handle(SENDER, envelope("RTC_ANSWER", answer));
        handler.handle(SENDER, envelope("RTC_ICE_CANDIDATE", candidate));
        handler.handle(SENDER, envelope("RTC_ICE_RESTART", negotiationOnly));
        handler.handle(SENDER, envelope("RTC_PEER_LEFT", negotiationOnly));

        verify(relayPublisher).publish(envelope("RTC_PEER_READY", negotiationOnly), "conn-1");
        verify(relayPublisher).publish(envelope("RTC_ANSWER", answer), "conn-1");
        verify(relayPublisher).publish(envelope("RTC_ICE_CANDIDATE", candidate), "conn-1");
        verify(relayPublisher).publish(envelope("RTC_ICE_RESTART", negotiationOnly), "conn-1");
        verify(relayPublisher).publish(envelope("RTC_PEER_LEFT", negotiationOnly), "conn-1");
    }

    @Test
    void negotiationId가_없거나_비어_있으면_거절한다() {
        assertMalformed("RTC_PEER_READY", null);
        assertMalformed("RTC_PEER_READY", MAPPER.createObjectNode());
        assertMalformed(
                "RTC_PEER_READY", MAPPER.createObjectNode().put("negotiationId", " "));
        assertMalformed(
                "RTC_PEER_READY", MAPPER.createObjectNode().put("negotiationId", 1));
    }

    @Test
    void 이벤트별_필수_객체가_없거나_타입이_다르면_거절한다() {
        JsonNode negotiationOnly =
                MAPPER.createObjectNode().put("negotiationId", "negotiation-1");
        assertMalformed("RTC_OFFER", negotiationOnly);
        assertMalformed("RTC_ANSWER", negotiationOnly);
        assertMalformed("RTC_ICE_CANDIDATE", negotiationOnly);
        assertMalformed(
                "RTC_OFFER",
                MAPPER.createObjectNode()
                        .put("negotiationId", "negotiation-1")
                        .put("description", "offer"));
        assertMalformed(
                "RTC_ICE_CANDIDATE",
                MAPPER.createObjectNode()
                        .put("negotiationId", "negotiation-1")
                        .put("candidate", "candidate:1"));
    }

    @Test
    void 계약에_없는_필드가_있으면_거절한다() {
        JsonNode payload =
                MAPPER.createObjectNode()
                        .put("negotiationId", "negotiation-1")
                        .put("unexpected", true);

        assertMalformed("RTC_PEER_LEFT", payload);
    }

    private void assertMalformed(String type, JsonNode payload) {
        assertThatThrownBy(() -> handler.handle(SENDER, envelope(type, payload)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(RealtimeErrorCode.MALFORMED_ENVELOPE);
        verify(relayPublisher, never()).publish(envelope(type, payload), "conn-1");
    }

    private RealtimeEnvelope envelope(String type, JsonNode payload) {
        return new RealtimeEnvelope(
                RealtimeEnvelope.CURRENT_VERSION,
                type,
                "req-1",
                RealtimeChannelType.CONSULTING_SESSION,
                "42",
                payload);
    }
}
