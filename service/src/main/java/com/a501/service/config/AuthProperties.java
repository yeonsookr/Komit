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
        // 환경변수가 없으면 Spring 이 "${KOMIT_USER_JWT_SECRET}" 글자 그대로를 바인딩합니다. 그 상태로 기동하지 않게 막습니다.
        if (userJwtSecret != null && userJwtSecret.startsWith("${")) {
            throw new IllegalArgumentException("KOMIT_USER_JWT_SECRET environment variable is not set");
        }
        if (kakaoTokenEncryptionKey != null && kakaoTokenEncryptionKey.startsWith("${")) {
            throw new IllegalArgumentException("KAKAO_TOKEN_ENCRYPTION_KEY environment variable is not set");
        }
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
