package com.a501.service.common.realtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.config.RealtimeProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/** 시험이 공유하는 값. 속성이 하나 늘 때마다 시험 파일을 모두 고치지 않게 한곳에 둔다. */
public final class RealtimeTestFixtures {
    public static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(20);
    public static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(60);

    private RealtimeTestFixtures() {}

    public static RealtimeProperties properties() {
        return properties(65536);
    }

    public static RealtimeProperties properties(int maxMessageBytes) {
        return new RealtimeProperties(
                Duration.ofSeconds(30),
                maxMessageBytes,
                20,
                40,
                3,
                Duration.ofSeconds(10),
                524288,
                HEARTBEAT_INTERVAL,
                HEARTBEAT_TIMEOUT,
                List.of());
    }

    public static RealtimePrincipal principal(String connectionId) {
        return principal(12, connectionId, "55");
    }

    public static RealtimePrincipal principal(int userId, String connectionId, String channelId) {
        return new RealtimePrincipal(
                userId, connectionId, RealtimeChannelType.CONSULTING_SESSION, channelId);
    }

    /**
     * 닫으면 실제로 닫히는 session. 열린 채로 남는 mock 을 쓰면, 닫힌 뒤에만 도는 정리 코드가
     * 시험에서만 안 돌아 통과하는 시험이 된다.
     */
    public static WebSocketSession openSession(List<String> sent) {
        WebSocketSession session = mock(WebSocketSession.class);
        AtomicBoolean open = new AtomicBoolean(true);
        when(session.isOpen()).thenAnswer(call -> open.get());
        try {
            org.mockito.Mockito.doAnswer(
                            call -> {
                                sent.add(
                                        ((WebSocketMessage<?>) call.getArgument(0))
                                                .getPayload()
                                                .toString());
                                return null;
                            })
                    .when(session)
                    .sendMessage(any());
            org.mockito.Mockito.doAnswer(
                            call -> {
                                open.set(false);
                                return null;
                            })
                    .when(session)
                    .close(any());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return session;
    }
}
