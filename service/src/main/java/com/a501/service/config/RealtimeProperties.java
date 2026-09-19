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
        List<String> allowedOrigins) {

    public RealtimeProperties {
        if (ticketTtl != null && (ticketTtl.isZero() || ticketTtl.isNegative())) {
            throw new IllegalArgumentException("Realtime ticket TTL must be positive");
        }
        // 비우면 Spring 이 같은 출처만 받는다. 운영의 기본값이라 없다고 기동을 막지 않는다.
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
