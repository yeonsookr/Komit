package com.a501.escrow.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.finance")
public record FinanceProperties(
        boolean enabled,
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotBlank String timeZone,
        @NotBlank String accountTypeUniqueNo
) {
}
