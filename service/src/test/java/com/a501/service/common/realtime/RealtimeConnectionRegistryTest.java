package com.a501.service.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.realtime.presence.RealtimePresenceService;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.config.RealtimeProperties;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

class RealtimeConnectionRegistryTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RealtimeConnectionRegistry registry;
    private RealtimePresenceService presence;

    @BeforeEach
    void setUp() {
        presence = mock(RealtimePresenceService.class);
        registry =
                new RealtimeConnectionRegistry(
                        MAPPER, presence, RealtimeTestFixtures.properties());
    }

    private RealtimePrincipal principal(String connectionId, String channelId) {
        return new RealtimePrincipal(
                12, connectionId, RealtimeChannelType.CONSULTING_SESSION, channelId);
    }

    private WebSocketSession openSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private RealtimeEnvelope envelope() {
        return new RealtimeEnvelope(
                RealtimeEnvelope.CURRENT_VERSION,
                "RTC_PEER_READY",
                "req-1",
                RealtimeChannelType.CONSULTING_SESSION,
                "55",
                null);
    }

    @Test
    void 등록한_연결은_connectionId_로_찾는다() {
        RealtimeConnection connection = registry.register(principal("conn-1", "55"), openSession());

        assertThat(registry.find("conn-1")).containsSame(connection);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void 같은_채널의_연결만_모아_준다() {
        registry.register(principal("conn-1", "55"), openSession());
        registry.register(principal("conn-2", "55"), openSession());
        registry.register(principal("conn-3", "56"), openSession());

        assertThat(registry.inChannel(RealtimeChannelType.CONSULTING_SESSION, "55"))
                .extracting(RealtimeConnection::connectionId)
                .containsExactlyInAnyOrder("conn-1", "conn-2");
    }

    @Test
    void 채널이_같아도_종류가_다르면_섞이지_않는다() {
        registry.register(principal("conn-1", "55"), openSession());
        registry.register(
                new RealtimePrincipal(12, "conn-2", RealtimeChannelType.CHAT_ROOM, "55"),
                openSession());

        assertThat(registry.inChannel(RealtimeChannelType.CONSULTING_SESSION, "55"))
                .extracting(RealtimeConnection::connectionId)
                .containsExactly("conn-1");
    }

    @Test
    void 연결을_지우면_채널_목록에서도_빠진다() {
        registry.register(principal("conn-1", "55"), openSession());
        registry.register(principal("conn-2", "55"), openSession());

        registry.forget(principal("conn-1", "55"));

        assertThat(registry.find("conn-1")).isEmpty();
        assertThat(registry.inChannel(RealtimeChannelType.CONSULTING_SESSION, "55"))
                .extracting(RealtimeConnection::connectionId)
                .containsExactly("conn-2");
    }

    @Test
    void 모르는_connectionId_를_지워도_터지지_않는다() {
        registry.forget(principal("없는-연결", "55"));

        assertThat(registry.size()).isZero();
    }

    @Test
    void 끝난_채널은_목록에서_치운다() {
        // 남겨 두면 끝난 세션 수만큼 map 이 계속 자란다.
        registry.register(principal("conn-1", "55"), openSession());
        registry.forget(principal("conn-1", "55"));

        assertThat(registry.inChannel(RealtimeChannelType.CONSULTING_SESSION, "55")).isEmpty();
        assertThat(registry.size()).isZero();
    }

    @Test
    void 보낸_것은_봉투_그대로_나간다() throws Exception {
        WebSocketSession session = openSession();
        RealtimeConnection connection = registry.register(principal("conn-1", "55"), session);

        connection.send(envelope());

        org.mockito.ArgumentCaptor<WebSocketMessage<?>> captured =
                org.mockito.ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(captured.capture());
        assertThat(MAPPER.readTree(captured.getValue().getPayload().toString()).get("type").asText())
                .isEqualTo("RTC_PEER_READY");
    }

    @Test
    void 여러_thread_가_같은_연결에_보내도_한_번에_하나씩만_나간다() throws Exception {
        // JSR-356 의 session 은 동시 전송을 허용하지 않는다. 겹치면 프레임이 깨진다.
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        WebSocketSession session = openSession();
        org.mockito.Mockito.doAnswer(
                        call -> {
                            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                            Thread.sleep(2);
                            inFlight.decrementAndGet();
                            return null;
                        })
                .when(session)
                .sendMessage(any());

        RealtimeConnection connection = registry.register(principal("conn-1", "55"), session);
        int senders = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(senders);
        for (int i = 0; i < senders; i++) {
            new Thread(
                            () -> {
                                try {
                                    start.await();
                                    connection.send(envelope());
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                } finally {
                                    done.countDown();
                                }
                            })
                    .start();
        }
        start.countDown();

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(maxInFlight.get()).isEqualTo(1);
        verify(session, org.mockito.Mockito.times(senders)).sendMessage(any());
    }

    @Test
    void 닫힌_연결에는_보내지_않는다() throws Exception {
        WebSocketSession session = openSession();
        RealtimeConnection connection = registry.register(principal("conn-1", "55"), session);
        when(session.isOpen()).thenReturn(false);

        connection.send(envelope());

        verify(session, org.mockito.Mockito.never()).sendMessage(any());
    }

    @Test
    void 봉투를_만들지_못하면_멀쩡한_연결을_끊지_않는다() throws Exception {
        // JacksonException 은 IOException 이라, 죽은 소켓과 같이 다루면 서버 결함 하나가
        // 그 채널의 모두를 끊어 버린다. 이건 우리 잘못이므로 연결은 그대로 둔다.
        ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any()))
                .thenThrow(
                        tools.jackson.core.exc.JacksonIOException.construct(
                                new java.io.IOException("nope")));
        RealtimeConnectionRegistry brokenRegistry =
                new RealtimeConnectionRegistry(
                        broken, presence, RealtimeTestFixtures.properties());
        WebSocketSession session = openSession();
        RealtimeConnection connection =
                brokenRegistry.register(principal("conn-1", "55"), session);

        connection.send(envelope());

        verify(session, org.mockito.Mockito.never()).sendMessage(any());
        verify(session, org.mockito.Mockito.never()).close(any());
        assertThat(connection.isOpen()).isTrue();
    }

    @Test
    void 보낼_것이_쌓여_한도를_넘으면_그_연결을_닫는다() throws Exception {
        // 계속 쌓으면 이 pod 의 메모리를 먹는다. 감싼 session 이 알려 주는 코드로 닫는다.
        WebSocketSession session = openSession();
        CloseStatus limit = new CloseStatus(4500, "buffer");
        org.mockito.Mockito.doThrow(
                        new org.springframework.web.socket.handler.SessionLimitExceededException(
                                "too much", limit))
                .when(session)
                .sendMessage(any(TextMessage.class));
        RealtimeConnection connection = registry.register(principal("conn-1", "55"), session);

        connection.send(envelope());

        verify(session).close(limit);
    }

    @Test
    void 보내다_난_다른_오류도_밖으로_새지_않는다() throws Exception {
        // 닫히는 중인 session 에 쓰면 container 가 IllegalStateException 을 던지기도 한다.
        // 새어 나가면 중계가 도는 중에 멈춰, 뒤에 있는 사람들이 이벤트를 아예 못 받는다.
        WebSocketSession session = openSession();
        org.mockito.Mockito.doThrow(new IllegalStateException("closing"))
                .when(session)
                .sendMessage(any(TextMessage.class));
        RealtimeConnection connection = registry.register(principal("conn-1", "55"), session);

        assertThatCode(() -> connection.send(envelope())).doesNotThrowAnyException();
        verify(session).close(CloseStatus.SERVER_ERROR);
    }

    @Test
    void 보내다_실패하면_그_연결만_닫고_던지지_않는다() throws Exception {
        // 한 사람에게 못 보낸 것 때문에 같은 채널의 다른 사람에게 보내는 일이 멈추면 안 된다.
        WebSocketSession session = openSession();
        org.mockito.Mockito.doThrow(new java.io.IOException("broken pipe"))
                .when(session)
                .sendMessage(any(TextMessage.class));
        RealtimeConnection connection = registry.register(principal("conn-1", "55"), session);

        connection.send(envelope());

        verify(session).close(CloseStatus.SERVER_ERROR);
    }
}
