package com.a501.service.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @NotBlank String endpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotNull Duration readUrlTtl) {

    public StorageProperties {
        if (readUrlTtl != null && (readUrlTtl.compareTo(Duration.ofSeconds(1)) < 0
                || readUrlTtl.compareTo(Duration.ofDays(7)) > 0)) {
            throw new IllegalArgumentException("Storage read URL TTL must be between 1 second and 7 days");
        }
    }
}
