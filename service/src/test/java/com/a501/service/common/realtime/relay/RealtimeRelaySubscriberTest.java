package com.a501.service.common.realtime.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.common.realtime.RealtimeConnection;
import com.a501.service.common.realtime.RealtimeConnectionRegistry;
import com.a501.service.common.realtime.RealtimePrincipal;
import com.a501.service.common.realtime.RealtimeTestFixtures;
import com.a501.service.common.realtime.presence.RealtimePresenceService;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.config.RealtimeProperties;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

class RealtimeRelaySubscriberTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RealtimeConnectionRegistry registry;
    private RealtimeRelaySubscriber subscriber;
    private RealtimePresenceService presence;

    @BeforeEach
    void setUp() {
        presence = mock(RealtimePresenceService.class);
        registry =
                new RealtimeConnectionRegistry(
                        MAPPER, presence, RealtimeTestFixtures.properties());
        subscriber = new RealtimeRelaySubscriber(registry, MAPPER);
    }

    private List<String> connect(String connectionId, String channelId) throws Exception {
        List<String> received = new ArrayList<>();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        org.mockito.Mockito.doAnswer(
                        call -> {
                            received.add(
                                    ((WebSocketMessage<?>) call.getArgument(0))
                                            .getPayload()
                                            .toString());
                            return null;
                        })
                .when(session)
                .sendMessage(org.mockito.ArgumentMatchers.any());
        registry.register(
                new RealtimePrincipal(
                        12, connectionId, RealtimeChannelType.CONSULTING_SESSION, channelId),
                session);
        return received;
    }

    private RealtimeEnvelope envelope(String channelId) {
        return new RealtimeEnvelope(
                RealtimeEnvelope.CURRENT_VERSION,
                "RTC_SDP_OFFER",
                "req-1",
                RealtimeChannelType.CONSULTING_SESSION,
                channelId,
                null);
    }

    private void receive(RealtimeRelayMessage relayed) throws Exception {
        receive(MAPPER.writeValueAsString(relayed));
    }

    private void receive(String body) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        subscriber.onMessage(message, RealtimeRelayTopic.PATTERN.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 같은_채널의_연결에_그대로_넣는다() throws Exception {
        List<String> received = connect("conn-1", "55");

        receive(new RealtimeRelayMessage(envelope("55"), null));

        assertThat(received).hasSize(1);
        assertThat(MAPPER.readTree(received.get(0)).get("type").asText()).isEqualTo("RTC_SDP_OFFER");
    }

    @Test
    void 보낸_당사자에게는_되돌려주지_않는다() throws Exception {
        // 보낸 pod 도 자기가 publish 한 것을 그대로 받는다. 여기서 걸러야 자기 SDP 를 되받지 않는다.
        List<String> sender = connect("conn-1", "55");
        List<String> peer = connect("conn-2", "55");

        receive(new RealtimeRelayMessage(envelope("55"), "conn-1"));

        assertThat(sender).isEmpty();
        assertThat(peer).hasSize(1);
    }

    @Test
    void 다른_채널의_연결에는_넣지_않는다() throws Exception {
        List<String> other = connect("conn-1", "56");

        receive(new RealtimeRelayMessage(envelope("55"), null));

        assertThat(other).isEmpty();
    }

    @Test
    void 이_pod_에_아무도_없으면_조용히_넘어간다() throws Exception {
        List<String> other = connect("conn-1", "56");

        receive(new RealtimeRelayMessage(envelope("55"), null));

        assertThat(other).isEmpty();
    }

    @Test
    void channel_이_빠진_봉투는_터지지_않고_버린다() throws Exception {
        // 형식만 맞고 channel 이 비면 뒤에서 NPE 가 난다. 그 예외는 읽기 실패와 달리 걸러지지
        // 않아 구독 전체가 오류로 물든다. 배포가 겹치면 실제로 올 수 있는 모양이다.
        List<String> received = connect("conn-1", "55");

        receive("{\"envelope\":{\"version\":1,\"type\":\"RTC_SDP_OFFER\",\"channelId\":\"55\"}}");
        receive(new RealtimeRelayMessage(envelope("55"), null));

        assertThat(received).hasSize(1);
    }

    @Test
    void 읽지_못하는_메시지는_그_한_건만_버린다() throws Exception {
        // 배포가 겹쳐 형식이 다른 pod 가 보낸 경우다. 여기서 터지면 구독이 끊긴다.
        List<String> received = connect("conn-1", "55");
        receive("{이건 JSON 이 아니다");
        receive(new RealtimeRelayMessage(envelope("55"), null));

        assertThat(received).hasSize(1);
    }

    @Test
    void 봉투가_빠진_메시지도_버린다() throws Exception {
        connect("conn-1", "55");

        receive(new RealtimeRelayMessage(null, null));

        assertThat(registry.size()).isEqualTo(1);
    }
}
