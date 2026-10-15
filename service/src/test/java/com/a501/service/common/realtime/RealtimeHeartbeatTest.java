package com.a501.service.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.realtime.presence.RealtimePresenceService;
import com.a501.service.common.realtime.relay.RealtimeControlPublisher;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeCloseStatus;
import com.a501.service.config.RealtimeProperties;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class RealtimeHeartbeatTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant START = Instant.parse("2026-09-20T09:00:00Z");
    private static final Duration TIMEOUT = RealtimeTestFixtures.HEARTBEAT_TIMEOUT;

    /** 시각을 앞으로 돌릴 수 있는 시계. 기다리지 않고 기한이 지난 상황을 만든다. */
    private static final class MovableClock extends Clock {
        private Instant now = START;

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }
    }

    private MovableClock clock;
    private RealtimeConnectionRegistry registry;
    private RealtimePresenceService presence;
    private RealtimeDuplicateConnections duplicates;
    private RealtimeHeartbeat heartbeat;

    @BeforeEach
    void setUp() {
        clock = new MovableClock();
        RealtimeProperties properties = RealtimeTestFixtures.properties();
        presence = mock(RealtimePresenceService.class);
        when(presence.touchAll(anyList())).thenReturn(List.of());
        registry = new RealtimeConnectionRegistry(MAPPER, presence, properties, clock);
        duplicates =
                new RealtimeDuplicateConnections(
                        presence, mock(RealtimeControlPublisher.class), registry);
        // 시험은 같은 thread 에서 끝내 보내기 결과를 바로 본다.
        heartbeat =
                new RealtimeHeartbeat(
                        registry, presence, duplicates, properties, clock, Runnable::run,
                        Runnable::run);
    }

    private RealtimeConnection connect(String connectionId, List<String> sink) {
        WebSocketSession session = RealtimeTestFixtures.openSession(sink);
        return registry.register(RealtimeTestFixtures.principal(connectionId), session);
    }

    @Test
    void 살아_있는_연결에는_PING_을_보낸다() throws Exception {
        List<String> sent = new ArrayList<>();
        connect("conn-1", sent);

        heartbeat.beat();

        assertThat(sent).hasSize(1);
        assertThat(MAPPER.readTree(sent.get(0)).get("type").asText()).isEqualTo("SYSTEM_PING");
        assertThat(MAPPER.readTree(sent.get(0)).get("channelId").asText()).isEqualTo("55");
    }

    @Test
    void 기한_안에_답이_없으면_연결을_닫고_registry_와_presence_에서_내린다() {
        List<String> sent = new ArrayList<>();
        RealtimeConnection connection = connect("conn-1", sent);

        clock.advance(TIMEOUT.plusSeconds(1));
        heartbeat.beat();

        assertThat(registry.find("conn-1")).isEmpty();
        assertThat(registry.size()).isZero();
        assertThat(sent).isEmpty();
        verify(presence).leave(connection.principal());
    }

    @Test
    void 아무_메시지나_들어오면_기한이_다시_늘어난다() {
        List<String> sent = new ArrayList<>();
        RealtimeConnection connection = connect("conn-1", sent);

        clock.advance(TIMEOUT.minusSeconds(1));
        connection.touch();
        clock.advance(TIMEOUT.minusSeconds(1));
        heartbeat.beat();

        assertThat(registry.find("conn-1")).isPresent();
        assertThat(sent).hasSize(1);
    }

    @Test
    void 한_연결을_치워도_나머지_차례가_밀리지_않는다() {
        List<String> alive = new ArrayList<>();
        List<String> silent = new ArrayList<>();
        RealtimeConnection answered = connect("conn-1", alive);
        connect("conn-2", silent);

        clock.advance(TIMEOUT.plusSeconds(1));
        answered.touch();
        heartbeat.beat();

        assertThat(registry.find("conn-1")).isPresent();
        assertThat(registry.find("conn-2")).isEmpty();
        assertThat(alive).hasSize(1);
        assertThat(silent).isEmpty();
    }

    @Test
    void presence_는_보내기_전에_늘린다() throws Exception {
        // 보내다 끊긴 연결은 그 과정에서 presence 에서도 내려간다. 순서를 뒤집으면 방금
        // 내려간 것을 다시 올려 놓아, 아무도 지우지 못하는 항목이 남는다.
        List<String> sent = new ArrayList<>();
        WebSocketSession session = RealtimeTestFixtures.openSession(sent);
        org.mockito.Mockito.doThrow(new IOException("broken pipe"))
                .when(session)
                .sendMessage(any(TextMessage.class));
        RealtimeConnection connection =
                registry.register(RealtimeTestFixtures.principal("conn-1"), session);

        heartbeat.beat();

        // 실제로 보내기 전에 늘렸는지를 본다. 뒤집히면 보내다 끊겨 presence 에서 내려간 항목을
        // 다시 올려 놓게 되고, 그 항목은 아무도 지우지 못한다.
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(presence, session);
        order.verify(presence).touchAll(List.of(connection.principal()));
        order.verify(session).sendMessage(any(TextMessage.class));
        // 끊긴 뒤 registry 와 presence 를 비우는 것은 container 가 afterConnectionClosed 로 알려
        // 줄 때 일어난다. 여기서는 연결이 닫혔다는 것까지 확인한다.
        assertThat(connection.isOpen()).isFalse();
    }

    @Test
    void 조용한_연결은_presence_를_늘리지_않는다() {
        RealtimeConnection connection = connect("conn-1", new ArrayList<>());

        clock.advance(TIMEOUT.plusSeconds(1));
        heartbeat.beat();

        verify(presence).touchAll(List.of());
        verify(presence).leave(connection.principal());
    }

    @Test
    void 대표_자리를_내준_예전_연결은_PONG을_보내도_닫는다() throws Exception {
        // 종료 지시가 Pub/Sub 에서 유실돼도 heartbeat 가 대표가 바뀐 것을 알아채고 접는다.
        List<String> sent = new ArrayList<>();
        RealtimeConnection stale = connect("conn-old", sent);
        when(presence.touchAll(List.of(stale.principal())))
                .thenReturn(List.of(stale.principal()));

        stale.touch();
        heartbeat.beat();

        assertThat(registry.find("conn-old")).isEmpty();
        verify(presence).leave(stale.principal());
    }

    @Test
    void 밀려난_연결은_지시로_접을_때와_같은_코드로_닫는다() throws Exception {
        // 1008 처럼 재접속을 부르는 코드로 닫으면 밀려난 탭이 다시 붙어 새 탭을 밀어내는
        // 고리가 생긴다. 지시를 받았을 때와 같은 4001 이어야 한다.
        List<String> sent = new ArrayList<>();
        WebSocketSession session = RealtimeTestFixtures.openSession(sent);
        RealtimeConnection stale =
                registry.register(RealtimeTestFixtures.principal("conn-old"), session);
        when(presence.touchAll(List.of(stale.principal())))
                .thenReturn(List.of(stale.principal()));

        heartbeat.beat();

        verify(session).close(RealtimeCloseStatus.DUPLICATE_CONNECTION);
        verify(session, org.mockito.Mockito.never()).close(CloseStatus.POLICY_VIOLATION);
        assertThat(MAPPER.readTree(sent.get(0)).get("type").asString())
                .isEqualTo("SYSTEM_REPLACED");
    }

    @Test
    void presence_갱신이_느려도_PING은_기다리지_않는다() {
        List<Runnable> delayedPresence = new ArrayList<>();
        Executor presenceExecutor = delayedPresence::add;
        heartbeat = new RealtimeHeartbeat(
                registry,
                presence,
                duplicates,
                RealtimeTestFixtures.properties(),
                clock,
                Runnable::run,
                presenceExecutor);
        List<String> sent = new ArrayList<>();
        connect("conn-1", sent);

        heartbeat.beat();

        assertThat(delayedPresence).hasSize(1);
        assertThat(sent).hasSize(1);
    }

    @Test
    void 종료가_대기하는_사이_PONG이_오면_연결을_유지한다() {
        List<Runnable> delayedSend = new ArrayList<>();
        heartbeat = new RealtimeHeartbeat(
                registry,
                presence,
                duplicates,
                RealtimeTestFixtures.properties(),
                clock,
                delayedSend::add,
                Runnable::run);
        RealtimeConnection connection = connect("conn-1", new ArrayList<>());
        clock.advance(TIMEOUT.plusSeconds(1));

        heartbeat.beat();
        connection.touch();
        delayedSend.forEach(Runnable::run);

        assertThat(registry.find("conn-1")).contains(connection);
    }

    @Test
    void pod_가_내려가면_남은_연결에_GOING_AWAY_를_알리고_presence_도_비운다() throws Exception {
        List<String> sent = new ArrayList<>();
        WebSocketSession session = RealtimeTestFixtures.openSession(sent);
        RealtimeConnection connection =
                registry.register(RealtimeTestFixtures.principal("conn-1"), session);

        registry.closeAll();

        verify(session).close(CloseStatus.GOING_AWAY);
        verify(presence).leave(connection.principal());
        assertThat(registry.size()).isZero();
        assertThat(registry.inChannel(RealtimeChannelType.CONSULTING_SESSION, "55")).isEmpty();
    }

    @Test
    void 닫히지_않은_연결은_목록에_남겨_다음_차례에_다시_본다() {
        // 감싼 session 은 다른 thread 가 닫는 중이면 아무 일도 하지 않는다. 그때 목록에서
        // 빼 버리면 heartbeat 가 다시는 그 연결을 보지 못해 영영 남는다.
        WebSocketSession stubborn = mock(WebSocketSession.class);
        when(stubborn.isOpen()).thenReturn(true);
        registry.register(RealtimeTestFixtures.principal("conn-1"), stubborn);

        assertThat(
                        registry.disconnect(
                                registry.find("conn-1").orElseThrow(), CloseStatus.GOING_AWAY))
                .isFalse();
        assertThat(registry.find("conn-1")).isPresent();
    }
}
