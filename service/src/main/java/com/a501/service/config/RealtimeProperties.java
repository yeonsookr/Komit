package com.a501.service.config;

import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.realtime")
public record RealtimeProperties(
        @NotNull Duration ticketTtl,
        @Positive int maxMessageBytes,
        @NotNull Duration sendTimeLimit,
        @Positive int sendBufferBytes,
        @NotNull Duration heartbeatInterval,
        @NotNull Duration heartbeatTimeout,
        List<String> allowedOrigins) {

    public RealtimeProperties {
        requirePositive(ticketTtl, "Realtime ticket TTL must be positive");
        requirePositive(sendTimeLimit, "Realtime send time limit must be positive");
        requirePositive(heartbeatInterval, "Realtime heartbeat interval must be positive");
        requirePositive(heartbeatTimeout, "Realtime heartbeat timeout must be positive");
        // 감싼 session 은 이 값을 밀리초 int 로 받는다. 넘치면 기동이 영문 모를 예외로 죽는다.
        if (sendTimeLimit != null && sendTimeLimit.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Realtime send time limit must be under 24 days");
        }
        // 한 번 도는 데 걸리는 시간과 답이 오는 시간이 있으므로 간격보다 조금 긴 정도로는 모자란다.
        // 묻기도 전에 기한이 지나 멀쩡한 연결이 매번 끊긴다.
        if (heartbeatInterval != null
                && heartbeatTimeout != null
                && heartbeatTimeout.compareTo(heartbeatInterval.multipliedBy(2)) < 0) {
            throw new IllegalArgumentException(
                    "Realtime heartbeat timeout must be at least twice the interval");
        }
        // 비우면 Spring 이 같은 출처만 받는다. 운영의 기본값이라 없다고 기동을 막지 않는다.
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    private static void requirePositive(Duration value, String message) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(message);
        }
    }
}
