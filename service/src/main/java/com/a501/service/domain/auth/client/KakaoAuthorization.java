package com.a501.service.domain.auth.client;

import java.time.Instant;

public record KakaoAuthorization(
        Long kakaoId,
        String nickname,
        String profileImageUrl,
        String email,
        boolean emailVerified,
        Boolean emailValid,
        Instant connectedAt,
        String refreshToken,
        long refreshTokenExpiresInSeconds) {
}
