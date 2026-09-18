package com.a501.service.config;

import java.net.URI;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.kakao")
public record KakaoProperties(
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotNull URI tokenUri,
        @NotNull URI userInfoUri) {

    public KakaoProperties {
        // 환경변수가 없으면 "${KAKAO_CLIENT_ID}" 글자 그대로가 들어와 @NotBlank 를 통과합니다. 그 상태로 기동하지 않게 막습니다.
        if (clientId != null && clientId.startsWith("${")) {
            throw new IllegalArgumentException("KAKAO_CLIENT_ID environment variable is not set");
        }
        if (clientSecret != null && clientSecret.startsWith("${")) {
            throw new IllegalArgumentException("KAKAO_CLIENT_SECRET environment variable is not set");
        }
    }
}
