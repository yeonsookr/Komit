package com.a501.service.common.realtime;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.SessionLimitExceededException;

/** 안에 든 session 은 감싼 것이라 동시에 보내도 프레임이 섞이지 않는다. */
@Slf4j
public final class RealtimeConnection {
    private final RealtimePrincipal principal;
    private final WebSocketSession session;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private volatile Instant lastSeenAt;

    RealtimeConnection(
            RealtimePrincipal principal,
            WebSocketSession session,
            ObjectMapper objectMapper,
            Clock clock) {
        this.principal = principal;
        this.session = session;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.lastSeenAt = clock.instant();
    }

    public RealtimePrincipal principal() {
        return principal;
    }

    public String connectionId() {
        return principal.connectionId();
    }

    public boolean isOpen() {
        return session.isOpen();
    }

    public void touch() {
        this.lastSeenAt = clock.instant();
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }

    /** 실패해도 던지지 않는다. 한 사람이 못 받는다고 같은 채널의 나머지가 멈추면 안 된다. */
    public void send(RealtimeEnvelope envelope) {
        if (envelope == null || !session.isOpen()) {
            return;
        }

        String text;
        try {
            text = objectMapper.writeValueAsString(envelope);
        } catch (JacksonException exception) {
            // 서버 결함이므로 연결을 끊지 않는다. 아래 IOException 과 같이 다루면 결함 하나가
            // 채널의 모두를 끊는다.
            log.error("realtime 봉투를 만들지 못했다: type={}", envelope.type(), exception);
            return;
        }

        try {
            session.sendMessage(new TextMessage(text));
        } catch (SessionLimitExceededException exception) {
            log.warn("realtime 전송 한도 초과로 연결을 닫는다: connectionId={}", connectionId(), exception);
            close(exception.getStatus());
        } catch (IOException exception) {
            // 탭을 닫으면 늘 여기로 온다. 드문 일이 아니라 warn 으로 남기지 않는다.
            log.debug("realtime 전송 실패로 연결을 닫는다: connectionId={}", connectionId(), exception);
            close(CloseStatus.SERVER_ERROR);
        } catch (RuntimeException exception) {
            // 닫히는 중인 session 은 IllegalStateException 을 던진다. 새어 나가면 중계가
            // 도는 중에 멈춰 뒤에 있는 사람들이 이 이벤트를 못 받는다.
            log.warn("realtime 전송 중 오류로 연결을 닫는다: connectionId={}", connectionId(), exception);
            close(CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * 감싼 session 은 다른 thread 가 닫는 중이면 아무 일도 하지 않고, 보낼 것이 쌓인 연결에는
     * 요청한 코드 대신 4500 을 쓴다. 닫혔는지는 {@link #isOpen} 으로 확인해야 한다.
     */
    public void close(CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException exception) {
            log.debug("realtime 연결 종료 실패: connectionId={}", connectionId(), exception);
        }
    }
}
