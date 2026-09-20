package com.a501.service.common.realtime;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.a501.service.common.realtime.presence.RealtimePresenceService;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.config.RealtimeProperties;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * 이 pod 에 붙어 있는 연결만 가진다. "세션에 누가 있는가" 는 presence 가 답한다.
 *
 * <p>치우는 길은 {@link #disconnect} 와 {@link #forget} 둘뿐이다. 자리마다 닫기·목록에서
 * 빼기·presence 지우기를 따로 적으면 그중 하나를 빠뜨린 자리가 생긴다.
 */
@Slf4j
@Component
public class RealtimeConnectionRegistry {
    private final ObjectMapper objectMapper;
    private final RealtimePresenceService presence;
    private final Clock clock;
    private final int sendTimeLimitMillis;
    private final int sendBufferBytes;

    private final Map<String, RealtimeConnection> byConnectionId = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> connectionIdsByChannel = new ConcurrentHashMap<>();

    @Autowired
    public RealtimeConnectionRegistry(
            ObjectMapper objectMapper,
            RealtimePresenceService presence,
            RealtimeProperties properties) {
        this(objectMapper, presence, properties, Clock.systemUTC());
    }

    RealtimeConnectionRegistry(
            ObjectMapper objectMapper,
            RealtimePresenceService presence,
            RealtimeProperties properties,
            Clock clock) {
        this.objectMapper = objectMapper;
        this.presence = presence;
        this.clock = clock;
        this.sendTimeLimitMillis = Math.toIntExact(properties.sendTimeLimit().toMillis());
        this.sendBufferBytes = properties.sendBufferBytes();
    }

    public RealtimeConnection register(RealtimePrincipal principal, WebSocketSession session) {
        // JSR-356 의 session 은 동시 전송을 허용하지 않는다. 두 참가자의 이벤트가 같은 순간에
        // 한 사람에게 가는 일이 흔하므로 감싸는 것은 선택이 아니다.
        RealtimeConnection connection =
                new RealtimeConnection(
                        principal,
                        new ConcurrentWebSocketSessionDecorator(
                                session, sendTimeLimitMillis, sendBufferBytes),
                        objectMapper,
                        clock);

        byConnectionId.put(principal.connectionId(), connection);
        connectionIdsByChannel.compute(
                key(principal.channel(), principal.channelId()),
                (channel, connectionIds) -> {
                    Set<String> ids =
                            connectionIds == null ? ConcurrentHashMap.newKeySet() : connectionIds;
                    ids.add(principal.connectionId());
                    return ids;
                });
        return connection;
    }

    /**
     * 닫기가 듣지 않는 경우가 있다. 그때 목록에서 빼 버리면 heartbeat 가 다시는 그 연결을
     * 보지 못해 영영 남으므로, 정말 닫혔을 때만 뺀다.
     */
    public boolean disconnect(RealtimeConnection connection, CloseStatus status) {
        connection.close(status);
        if (connection.isOpen()) {
            log.warn("realtime 연결이 닫히지 않아 목록에 남겨 둔다: connectionId={}",
                    connection.connectionId());
            return false;
        }
        forget(connection.principal());
        return true;
    }

    public void forget(RealtimePrincipal principal) {
        unregister(principal.connectionId());
        presence.leave(principal);
    }

    void unregister(String connectionId) {
        RealtimeConnection removed = byConnectionId.remove(connectionId);
        if (removed == null) {
            return;
        }
        // 빈 집합을 남기면 끝난 채널만큼 map 이 자란다. compute 라야 register 와 겹치지 않는다.
        connectionIdsByChannel.compute(
                key(removed.principal().channel(), removed.principal().channelId()),
                (channel, connectionIds) -> {
                    if (connectionIds == null) {
                        return null;
                    }
                    connectionIds.remove(connectionId);
                    return connectionIds.isEmpty() ? null : connectionIds;
                });
    }

    public Optional<RealtimeConnection> find(String connectionId) {
        return Optional.ofNullable(byConnectionId.get(connectionId));
    }

    /** 다른 pod 의 참가자는 여기 없다. */
    public List<RealtimeConnection> inChannel(RealtimeChannelType channel, String channelId) {
        return connectionIdsByChannel.getOrDefault(key(channel, channelId), Set.of()).stream()
                .map(byConnectionId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<RealtimeConnection> all() {
        return List.copyOf(byConnectionId.values());
    }

    public int size() {
        return byConnectionId.size();
    }

    /**
     * 그냥 죽으면 클라이언트는 1006 을 보고 오류로 알아본다. 재배포는 오류가 아니다.
     *
     * <p>{@code DisposableBean} 으로 두면 늦다. bean 을 없애는 것은 Tomcat 을 멈춘 뒤라
     * 그때는 보낼 길이 없다. {@code ContextClosedEvent} 는 그보다 먼저 온다.
     */
    @EventListener(ContextClosedEvent.class)
    public void closeAll() {
        for (RealtimeConnection connection : all()) {
            disconnect(connection, CloseStatus.GOING_AWAY);
        }
    }

    private String key(RealtimeChannelType channel, String channelId) {
        return channel.name() + ":" + channelId;
    }
}
