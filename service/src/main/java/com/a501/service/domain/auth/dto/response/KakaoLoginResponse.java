package com.a501.service.domain.auth.dto.response;

public record KakaoLoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        boolean isNewUser,
        LoginUserResponse user) {
}
