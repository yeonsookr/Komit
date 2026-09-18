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
}
