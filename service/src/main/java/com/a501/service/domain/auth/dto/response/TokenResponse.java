package com.a501.service.domain.auth.dto.response;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
}
