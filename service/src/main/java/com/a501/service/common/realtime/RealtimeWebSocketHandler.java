package com.a501.service.common.realtime;

import java.nio.charset.StandardCharsets;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.ErrorCode;
import com.a501.service.common.realtime.presence.RealtimePresenceService;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeErrorCode;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;
import com.a501.service.common.realtime.protocol.RealtimeSystemEvent;
import com.a501.service.config.RealtimeProperties;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {
    private final RealtimeDispatcher dispatcher;
    private final RealtimeConnectionRegistry registry;
    private final RealtimePresenceService presence;
    private final RealtimeDuplicateConnections duplicates;
    private final ObjectMapper objectMapper;
    private final int maxMessageBytes;

    public RealtimeWebSocketHandler(
            RealtimeDispatcher dispatcher,
            RealtimeConnectionRegistry registry,
            RealtimePresenceService presence,
            RealtimeDuplicateConnections duplicates,
            ObjectMapper objectMapper,
            RealtimeProperties properties) {
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.presence = presence;
        this.duplicates = duplicates;
        this.objectMapper = objectMapper;
        this.maxMessageBytes = properties.maxMessageBytes();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        RealtimePrincipal principal = principal(session);
        if (principal == null) {
            // handshake 를 통과했다면 있어야 한다. 없으면 붙인 자리가 잘못된 것이다.
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }
        registry.register(principal, session);
        presence.touch(principal);
        duplicates.closeOlderOf(principal);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        RealtimePrincipal principal = principal(session);
        if (principal != null) {
            registry.forget(principal);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        RealtimePrincipal principal = principal(session);
        RealtimeConnection connection =
                principal == null ? null : registry.find(principal.connectionId()).orElse(null);
        if (connection == null) {
            // 이미 걷어 갔거나 pod 가 내려가는 중이다. 서버 결함이 아니므로 1011 이 아니라
            // 1001 로 알리고, 감싸지 않은 session 으로는 답장하지 않는다.
            session.close(CloseStatus.GOING_AWAY);
            return;
        }

        // 내용이 무엇이든 들어왔다면 살아 있다는 뜻이라, 보기 전에 먼저 기록한다.
        connection.touch();

        RealtimeEnvelope parsed = null;
        try {
            parsed = parse(message);
            if (parsed.family() == RealtimeEventFamily.SYSTEM) {
                // 연결 확인은 공통 계층이 여기서 끝낸다. domain handler 로 내려보내지 않는다.
                connection.send(system(parsed));
                return;
            }
            dispatcher.dispatch(principal, parsed);
        } catch (BusinessException exception) {
            connection.send(error(exception.getErrorCode(), parsed));
        }
    }

    private RealtimeEnvelope parse(TextMessage message) {
        String payload = message.getPayload();
        if (payload.getBytes(StandardCharsets.UTF_8).length > maxMessageBytes) {
            throw new BusinessException(RealtimeErrorCode.PAYLOAD_TOO_LARGE);
        }
        try {
            return RealtimeEnvelope.validated(objectMapper.readValue(payload, RealtimeEnvelope.class));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            // 모르는 channel 값이나 깨진 JSON 은 Jackson 이 먼저 막는다. 그 실패도 계약 형식으로 내보낸다.
            throw new BusinessException(RealtimeErrorCode.MALFORMED_ENVELOPE);
        }
    }

    private RealtimeEnvelope system(RealtimeEnvelope envelope) {
        if (RealtimeSystemEvent.SYSTEM_PING.name().equals(envelope.type())) {
            return envelope.reply(RealtimeSystemEvent.SYSTEM_PONG.name(), null);
        }
        if (RealtimeSystemEvent.SYSTEM_PONG.name().equals(envelope.type())) {
            // 서버가 보낸 ping 의 답이다. 돌려줄 것이 없다.
            return null;
        }
        throw new BusinessException(RealtimeErrorCode.UNKNOWN_EVENT_TYPE);
    }

    /** 실패를 알린다. 어느 요청의 실패인지 잇도록 읽어낸 만큼은 그대로 싣는다. */
    private RealtimeEnvelope error(ErrorCode errorCode, RealtimeEnvelope parsed) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("code", errorCode.code());
        payload.put("message", errorCode.message());

        if (parsed == null) {
            return new RealtimeEnvelope(
                    RealtimeEnvelope.CURRENT_VERSION,
                    RealtimeSystemEvent.SYSTEM_ERROR.name(),
                    null,
                    null,
                    null,
                    payload);
        }
        return parsed.reply(RealtimeSystemEvent.SYSTEM_ERROR.name(), payload);
    }

    static RealtimePrincipal principal(WebSocketSession session) {
        Object stored = session.getAttributes().get(RealtimeHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
        return stored instanceof RealtimePrincipal principal ? principal : null;
    }
}
