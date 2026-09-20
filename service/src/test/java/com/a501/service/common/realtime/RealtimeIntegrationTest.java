package com.a501.service.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.common.realtime.presence.RealtimePresence;
import com.a501.service.common.realtime.presence.RealtimePresenceService;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.ticket.RealtimeTicket;
import com.a501.service.common.realtime.ticket.RealtimeTicketService;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * 진짜 Tomcat, 진짜 Redis 로 연결 한 번을 끝까지 열어 본다. 단위 시험은 mock 이라 Lua 스크립트와
 * handshake 를 지나가지 않아, 여기서만 드러나는 것들이 있다.
 */
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RealtimeIntegrationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CHANNEL_ID = "55";
    private static final int USER_ID = 12;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    @LocalServerPort int port;

    @Autowired RealtimeTicketService ticketService;
    @Autowired RealtimePresenceService presence;
    @Autowired RealtimeConnectionRegistry registry;

    /** 받은 것을 모아 두는 클라이언트. */
    private static final class Recorder extends TextWebSocketHandler {
        final List<String> received = new ArrayList<>();
        volatile CloseStatus closedWith;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            synchronized (received) {
                received.add(message.getPayload());
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closedWith = status;
        }

        List<String> snapshot() {
            synchronized (received) {
                return List.copyOf(received);
            }
        }
    }

    private WebSocketSession open(Recorder recorder) throws Exception {
        String ticket =
                ticketService.issue(
                        new RealtimeTicket(
                                USER_ID, RealtimeChannelType.CONSULTING_SESSION, CHANNEL_ID));
        return new StandardWebSocketClient()
                .execute(
                        recorder,
                        new WebSocketHttpHeaders(),
                        URI.create(
                                "ws://localhost:" + port + "/ws/realtime?ticket=" + ticket))
                .get(10, TimeUnit.SECONDS);
    }

    private void awaitUntil(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("기다리던 상태가 되지 않았다");
    }

    private String envelope(String type) {
        return "{\"version\":1,\"type\":\"" + type
                + "\",\"requestId\":\"req-1\",\"channel\":\"CONSULTING_SESSION\",\"channelId\":\""
                + CHANNEL_ID + "\"}";
    }

    @Test
    void 티켓으로_열고_PING_에_PONG_을_받는다() throws Exception {
        Recorder recorder = new Recorder();
        WebSocketSession session = open(recorder);
        try {
            assertThat(session.isOpen()).isTrue();

            session.sendMessage(new TextMessage(envelope("SYSTEM_PING")));

            awaitUntil(() -> !recorder.snapshot().isEmpty());
            assertThat(MAPPER.readTree(recorder.snapshot().get(0)).get("type").asString())
                    .isEqualTo("SYSTEM_PONG");
        } finally {
            session.close();
        }
    }

    @Test
    void 티켓은_한_번만_쓰인다() throws Exception {
        String ticket =
                ticketService.issue(
                        new RealtimeTicket(
                                USER_ID, RealtimeChannelType.CONSULTING_SESSION, CHANNEL_ID));
        URI uri = URI.create("ws://localhost:" + port + "/ws/realtime?ticket=" + ticket);

        WebSocketSession first =
                new StandardWebSocketClient()
                        .execute(new Recorder(), new WebSocketHttpHeaders(), uri)
                        .get(10, TimeUnit.SECONDS);
        try {
            assertThat(first.isOpen()).isTrue();
            assertThat(
                            org.assertj.core.api.Assertions.catchThrowable(
                                    () ->
                                            new StandardWebSocketClient()
                                                    .execute(
                                                            new Recorder(),
                                                            new WebSocketHttpHeaders(),
                                                            uri)
                                                    .get(10, TimeUnit.SECONDS)))
                    .isNotNull();
        } finally {
            first.close();
        }
    }

    @Test
    void 티켓_없이는_열리지_않는다() {
        URI uri = URI.create("ws://localhost:" + port + "/ws/realtime");

        assertThat(
                        org.assertj.core.api.Assertions.catchThrowable(
                                () ->
                                        new StandardWebSocketClient()
                                                .execute(
                                                        new Recorder(),
                                                        new WebSocketHttpHeaders(),
                                                        uri)
                                                .get(10, TimeUnit.SECONDS)))
                .isNotNull();
    }

    @Test
    void presence_에_기록되고_나가면_지워진다() throws Exception {
        // Lua 스크립트는 mock 시험을 지나가지 않는다. 진짜 Redis 에서만 드러난다.
        Recorder recorder = new Recorder();
        WebSocketSession session = open(recorder);
        try {
            awaitUntil(() -> !present().isEmpty());
            assertThat(present())
                    .extracting(RealtimePresence::userId)
                    .containsExactly(USER_ID);
        } finally {
            session.close();
        }

        awaitUntil(() -> present().isEmpty());
    }

    @Test
    void 같은_사람이_다시_들어오면_앞의_연결이_접힌다() throws Exception {
        Recorder first = new Recorder();
        WebSocketSession firstSession = open(first);
        awaitUntil(() -> registry.size() == 1);

        Recorder second = new Recorder();
        WebSocketSession secondSession = open(second);
        try {
            // 앞의 연결은 이유를 받고 닫힌다. 닫는 코드만으로는 이유를 전할 수 없어 봉투를 먼저 보낸다.
            awaitUntil(() -> !first.snapshot().isEmpty());
            assertThat(MAPPER.readTree(first.snapshot().get(0)).get("type").asString())
                    .isEqualTo("SYSTEM_REPLACED");

            awaitUntil(() -> !firstSession.isOpen());
            assertThat(secondSession.isOpen()).isTrue();
            assertThat(second.snapshot()).isEmpty();
        } finally {
            secondSession.close();
        }
    }

    private List<RealtimePresence> present() {
        return presence.inChannel(RealtimeChannelType.CONSULTING_SESSION, CHANNEL_ID);
    }
}
