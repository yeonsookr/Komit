package com.a501.service.domain.account.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ConfirmAccountVerificationRequest(
        @NotNull @Pattern(regexp = "\\d{4}") String authCode) {}
