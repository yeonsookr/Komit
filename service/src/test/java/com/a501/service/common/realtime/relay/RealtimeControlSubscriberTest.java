package com.a501.service.common.realtime.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.realtime.RealtimeConnectionRegistry;
import com.a501.service.common.realtime.RealtimeTestFixtures;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeCloseStatus;
import com.a501.service.config.RealtimeProperties;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class RealtimeControlSubscriberTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RealtimeConnectionRegistry registry;
    private RealtimeControlSubscriber subscriber;

    @BeforeEach
    void setUp() {
        registry =
                new RealtimeConnectionRegistry(
                        MAPPER,
                        mock(com.a501.service.common.realtime.presence.RealtimePresenceService.class),
                        com.a501.service.common.realtime.RealtimeTestFixtures.properties());
        subscriber =
                new RealtimeControlSubscriber(
                        registry,
                        new com.a501.service.common.realtime.RealtimeDuplicateConnections(
                                mock(com.a501.service.common.realtime.presence.RealtimePresenceService.class),
                                mock(RealtimeControlPublisher.class),
                                registry),
                        MAPPER);
    }

    private final List<String> sent = new ArrayList<>();

    private WebSocketSession connect(String connectionId) {
        WebSocketSession session = RealtimeTestFixtures.openSession(sent);
        registry.register(RealtimeTestFixtures.principal(connectionId), session);
        return session;
    }

    private void receive(String body) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        subscriber.onMessage(message, null);
    }

    private void receive(RealtimeControlMessage control) throws Exception {
        receive(MAPPER.writeValueAsString(control));
    }

    @Test
    void 지목된_연결을_4001_로_닫고_내린다() throws Exception {
        WebSocketSession session = connect("conn-1");

        receive(new RealtimeControlMessage(RealtimeControlAction.CLOSE_DUPLICATE, "conn-1"));

        verify(session).close(RealtimeCloseStatus.DUPLICATE_CONNECTION);
        assertThat(registry.find("conn-1")).isEmpty();
    }

    @Test
    void 닫기_전에_이유를_먼저_보낸다() throws Exception {
        // 닫는 코드만으로는 이유를 전할 수 없다. 보낼 것이 쌓인 연결에는 감싼 session 이
        // 요청한 코드 대신 자기 코드를 쓰고, 아예 닫히지 않는 경우도 있다.
        connect("conn-1");

        receive(new RealtimeControlMessage(RealtimeControlAction.CLOSE_DUPLICATE, "conn-1"));

        assertThat(sent).hasSize(1);
        assertThat(MAPPER.readTree(sent.get(0)).get("type").asText()).isEqualTo("SYSTEM_REPLACED");
        assertThat(RealtimeCloseStatus.DUPLICATE_CONNECTION.getCode()).isEqualTo(4001);
    }

    @Test
    void 이_pod_에_없는_연결이면_아무_일도_하지_않는다() throws Exception {
        WebSocketSession mine = connect("conn-1");

        receive(new RealtimeControlMessage(RealtimeControlAction.CLOSE_DUPLICATE, "conn-2"));

        verify(mine, never()).close(any());
        assertThat(registry.find("conn-1")).isPresent();
    }

    @Test
    void 모르는_지시는_그_한_건만_버린다() throws Exception {
        // 배포가 겹쳐 새 pod 가 보낸 지시다. 여기서 터지면 구독이 끊긴다.
        WebSocketSession session = connect("conn-1");

        receive("{\"action\":\"LATER_ACTION\",\"connectionId\":\"conn-1\"}");
        receive("{이건 JSON 이 아니다");
        receive(new RealtimeControlMessage(RealtimeControlAction.CLOSE_DUPLICATE, "conn-1"));

        verify(session).close(RealtimeCloseStatus.DUPLICATE_CONNECTION);
    }
}
