package com.a501.service.domain.account.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RequestAccountVerificationRequest(
        @NotNull Integer bankCodeId,
        @NotNull @Pattern(regexp = "\\d{16}") String accountNumber,
        @NotNull @Size(min = 1, message = "{komit.minLength}") String accountHolder) {}
