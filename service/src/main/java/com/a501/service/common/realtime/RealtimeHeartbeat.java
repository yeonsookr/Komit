package com.a501.service.common.realtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import com.a501.service.common.realtime.presence.RealtimePresenceService;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeSystemEvent;
import com.a501.service.config.RealtimeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

/** TCP 가 끊겨도 서버는 한동안 모른다. 주기적으로 말을 걸어 사라진 연결을 걷어 낸다. */
@Slf4j
@Component
public class RealtimeHeartbeat implements DisposableBean {
    private final RealtimeConnectionRegistry registry;
    private final RealtimePresenceService presence;
    private final RealtimeDuplicateConnections duplicates;
    private final Duration timeout;
    private final Clock clock;

    /**
     * 보내기와 닫기는 상대가 가져가지 않으면 막힌다. {@code @Scheduled} 는 thread 가 하나라
     * 그 자리에서 보내면 막힌 사람 하나가 다음 차례를 미루고, 멀쩡한 연결이 조용하다는 이유로
     * 끊긴다. 막힐 수 있는 일은 전부 여기로 넘긴다.
     */
    private final Executor sendExecutor;
    private final Executor presenceExecutor;

    private final ThreadPoolTaskExecutor ownedSendExecutor;
    private final ThreadPoolTaskExecutor ownedPresenceExecutor;

    @Autowired
    public RealtimeHeartbeat(
            RealtimeConnectionRegistry registry,
            RealtimePresenceService presence,
            RealtimeDuplicateConnections duplicates,
            RealtimeProperties properties) {
        this(registry, presence, duplicates, properties, Clock.systemUTC(), null, null);
    }

    RealtimeHeartbeat(
            RealtimeConnectionRegistry registry,
            RealtimePresenceService presence,
            RealtimeDuplicateConnections duplicates,
            RealtimeProperties properties,
            Clock clock,
            Executor sendExecutor,
            Executor presenceExecutor) {
        this.registry = registry;
        this.presence = presence;
        this.duplicates = duplicates;
        this.timeout = properties.heartbeatTimeout();
        this.clock = clock;
        this.ownedSendExecutor = sendExecutor == null ? createSendExecutor() : null;
        this.ownedPresenceExecutor = presenceExecutor == null ? createPresenceExecutor() : null;
        this.sendExecutor = ownedSendExecutor != null ? ownedSendExecutor : sendExecutor;
        this.presenceExecutor =
                ownedPresenceExecutor != null ? ownedPresenceExecutor : presenceExecutor;
    }

    @Scheduled(fixedDelayString = "${app.realtime.heartbeat-interval}")
    void beat() {
        Instant silentSince = clock.instant().minus(timeout);
        List<RealtimeConnection> live = new ArrayList<>();

        for (RealtimeConnection connection : registry.all()) {
            if (connection.lastSeenAt().isBefore(silentSince)) {
                log.debug("realtime 응답이 없어 연결을 닫는다: connectionId={}", connection.connectionId());
                sendExecutor.execute(() -> disconnectIfStillSilent(connection, silentSince));
                continue;
            }
            live.add(connection);
        }

        renewPresence(live);

        for (RealtimeConnection connection : live) {
            sendExecutor.execute(() -> {
                if (isCurrentOpenConnection(connection)) {
                    connection.send(ping(connection.principal()));
                }
            });
        }
    }

    @Override
    public void destroy() {
        if (ownedSendExecutor != null) {
            ownedSendExecutor.shutdown();
        }
        if (ownedPresenceExecutor != null) {
            ownedPresenceExecutor.shutdown();
        }
    }

    private void renewPresence(List<RealtimeConnection> candidates) {
        try {
            presenceExecutor.execute(() -> {
                List<RealtimeConnection> current = candidates.stream()
                        .filter(this::isCurrentOpenConnection)
                        .toList();
                List<RealtimePrincipal> noLongerOwners = presence.touchAll(
                        current.stream().map(RealtimeConnection::principal).toList());
                for (RealtimePrincipal principal : noLongerOwners) {
                    registry.find(principal.connectionId())
                            .filter(connection -> connection.principal().equals(principal))
                            .ifPresent(duplicates::replace);
                }

                Set<String> replaced = noLongerOwners.stream()
                        .map(RealtimePrincipal::connectionId)
                        .collect(java.util.stream.Collectors.toSet());
                for (RealtimeConnection connection : current) {
                    if (connection.isOpen() || replaced.contains(connection.connectionId())) {
                        continue;
                    }
                    // 보내다 끊긴 연결이다. 위 touchAll 이 presence 에 다시 올렸을 수 있으므로,
                    // 이미 목록에서 빠졌더라도 presence 는 확실히 내린다.
                    if (registry.find(connection.connectionId())
                            .filter(registered -> registered == connection)
                            .isPresent()) {
                        registry.forget(connection.principal());
                    } else {
                        presence.leave(connection.principal());
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            log.warn("realtime presence 갱신이 밀려 이번 차례를 건너뛴다", exception);
        }
    }

    private void disconnectIfStillSilent(RealtimeConnection connection, Instant silentSince) {
        if (isCurrentOpenConnection(connection)
                && connection.lastSeenAt().isBefore(silentSince)) {
            registry.disconnect(connection, CloseStatus.GOING_AWAY);
        }
    }

    private boolean isCurrentOpenConnection(RealtimeConnection connection) {
        return connection.isOpen()
                && registry.find(connection.connectionId())
                        .filter(current -> current == connection)
                        .isPresent();
    }

    private RealtimeEnvelope ping(RealtimePrincipal principal) {
        return new RealtimeEnvelope(
                RealtimeEnvelope.CURRENT_VERSION,
                RealtimeSystemEvent.SYSTEM_PING.name(),
                null,
                principal.channel(),
                principal.channelId(),
                null);
    }

    private static ThreadPoolTaskExecutor createSendExecutor() {
        // bean 으로 올리지 않는다. Executor 타입 bean 이 있으면 Spring Boot 가
        // applicationTaskExecutor 를 만들지 않아 MVC 의 비동기 처리까지 딸려 바뀐다.
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("realtime-heartbeat-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        // 끊기 지시를 버리면 죽은 연결이 영영 남으므로 부른 thread 가 직접 한다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    private static ThreadPoolTaskExecutor createPresenceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("realtime-presence-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
