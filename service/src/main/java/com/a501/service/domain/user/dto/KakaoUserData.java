package com.a501.service.domain.user.dto;

import java.time.Instant;

public record KakaoUserData(
        Long kakaoId,
        String nickname,
        String profileImageUrl,
        String email,
        boolean emailVerified,
        Boolean emailValid,
        Instant connectedAt,
        String kakaoRefreshToken,
        Instant kakaoRefreshTokenExpiresAt) {
}
