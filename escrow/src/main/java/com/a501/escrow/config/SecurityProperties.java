package com.a501.escrow.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @NotBlank @Size(min = 32) String clientApiKeyHmacSecret,
        @NotBlank @Size(min = 32) String sensitiveDataHmacSecret,
        @NotBlank String encryptionKeyBase64,
        @Min(1) int encryptionKeyVersion,
        @NotBlank @Size(min = 32) String operatorApiKey
) {
}
