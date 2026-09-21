package com.a501.service.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @NotBlank String internalEndpoint,
        @NotBlank String publicEndpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucket,
        /** 버킷 리전. 값을 고정해 두면 SDK가 주소 발급마다 리전을 조회하러 저장소를 호출하지 않습니다. */
        @NotBlank String region,
        @NotNull Duration uploadUrlTtl,
        @NotNull Duration readUrlTtl,
        long maxFileBytes) {

    public StorageProperties {
        validateTtl("upload", uploadUrlTtl);
        validateTtl("read", readUrlTtl);
        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException("Storage maximum file size must be positive");
        }
    }

    private static void validateTtl(String kind, Duration ttl) {
        if (ttl != null && (ttl.compareTo(Duration.ofSeconds(1)) < 0
                || ttl.compareTo(Duration.ofDays(7)) > 0)) {
            throw new IllegalArgumentException(
                    "Storage " + kind + " URL TTL must be between 1 second and 7 days");
        }
    }
}
