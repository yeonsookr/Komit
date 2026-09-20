package com.a501.service.common.realtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
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
    private final Duration timeout;
    private final Clock clock;

    /**
     * 보내기와 닫기는 상대가 가져가지 않으면 막힌다. {@code @Scheduled} 는 thread 가 하나라
     * 그 자리에서 보내면 막힌 사람 하나가 다음 차례를 미루고, 멀쩡한 연결이 조용하다는 이유로
     * 끊긴다. 막힐 수 있는 일은 전부 여기로 넘긴다.
     */
    private final Executor sendExecutor;

    private final ThreadPoolTaskExecutor ownedExecutor;

    @Autowired
    public RealtimeHeartbeat(
            RealtimeConnectionRegistry registry,
            RealtimePresenceService presence,
            RealtimeProperties properties) {
        this(registry, presence, properties, Clock.systemUTC(), null);
    }

    RealtimeHeartbeat(
            RealtimeConnectionRegistry registry,
            RealtimePresenceService presence,
            RealtimeProperties properties,
            Clock clock,
            Executor sendExecutor) {
        this.registry = registry;
        this.presence = presence;
        this.timeout = properties.heartbeatTimeout();
        this.clock = clock;
        this.ownedExecutor = sendExecutor == null ? createSendExecutor() : null;
        this.sendExecutor = ownedExecutor != null ? ownedExecutor : sendExecutor;
    }

    @Scheduled(fixedDelayString = "${app.realtime.heartbeat-interval}")
    void beat() {
        Instant silentSince = clock.instant().minus(timeout);
        List<RealtimeConnection> live = new ArrayList<>();

        for (RealtimeConnection connection : registry.all()) {
            if (connection.lastSeenAt().isBefore(silentSince)) {
                log.debug("realtime 응답이 없어 연결을 닫는다: connectionId={}", connection.connectionId());
                sendExecutor.execute(() -> registry.disconnect(connection, CloseStatus.GOING_AWAY));
                continue;
            }
            live.add(connection);
        }

        // 보내기 전에 늘린다. 보내다 끊긴 연결은 그 과정에서 presence 에서도 내려가므로,
        // 뒤집으면 방금 내려간 것을 다시 올려 아무도 지우지 못하는 항목이 남는다.
        presence.touchAll(live.stream().map(RealtimeConnection::principal).toList());

        for (RealtimeConnection connection : live) {
            sendExecutor.execute(() -> connection.send(ping(connection.principal())));
        }
    }

    @Override
    public void destroy() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
        }
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
}
