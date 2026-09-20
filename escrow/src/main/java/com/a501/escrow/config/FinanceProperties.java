package com.a501.escrow.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.finance")
public record FinanceProperties(
        boolean enabled,
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotBlank String timeZone,
        @NotBlank String accountTypeUniqueNo,
        @NotBlank String escrowUserKey,
        @NotBlank String escrowAccountNo,
        @NotBlank String feeAccountNo,
        @Positive @Max(30) int virtualAccountValidityDays
) {
}
