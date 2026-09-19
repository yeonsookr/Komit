package com.a501.service.domain.auth.dto.request;

import jakarta.validation.constraints.NotNull;

public record RefreshTokenRequest(@NotNull String refreshToken) {
}
