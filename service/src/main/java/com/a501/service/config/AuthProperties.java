package com.a501.service.config;

import java.time.Duration;
import java.nio.charset.StandardCharsets;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @NotBlank String userJwtSecret,
        @NotBlank String kakaoTokenEncryptionKey,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {

    public AuthProperties {
        if (userJwtSecret != null && userJwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("User JWT secret must be at least 32 bytes");
        }
        if (accessTokenTtl != null && (accessTokenTtl.isZero() || accessTokenTtl.isNegative())) {
            throw new IllegalArgumentException("Access token TTL must be positive");
        }
        if (refreshTokenTtl != null && (refreshTokenTtl.isZero() || refreshTokenTtl.isNegative())) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }
    }
}
