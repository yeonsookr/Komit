package com.a501.service.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.config.AuthProperties;
import com.a501.service.domain.auth.AuthErrorCode;
import com.a501.service.domain.auth.client.KakaoAuthorization;
import com.a501.service.domain.auth.client.KakaoOauthClient;
import com.a501.service.domain.auth.repository.RefreshTokenRepository;
import com.a501.service.domain.user.dto.UserLoginResult;
import com.a501.service.domain.user.dto.UserSnapshot;
import com.a501.service.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private KakaoOauthClient kakaoOauthClient;
    @Mock
    private UserService userService;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private JwtTokenProvider tokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(new AuthProperties(
                "user-jwt-secret-for-tests-at-least-32-bytes",
                "ZGV2LW9ubHkta2FrYW8tdG9rZW4ta2V5LTMyYnl0ZXM=",
                Duration.ofMinutes(15),
                Duration.ofDays(14)));
        authService = new AuthService(kakaoOauthClient, userService, tokenProvider, refreshTokenRepository);
    }

    @Test
    void loginReturnsContractResponseAndStoresOneRefreshSession() {
        when(kakaoOauthClient.authorize("code", "https://front/callback"))
                .thenReturn(authorization());
        when(userService.loginOrRegister(any()))
                .thenReturn(UserLoginResult.accepted(new UserSnapshot(12, "김미미", null, false), true));
        when(refreshTokenRepository.issue(12)).thenReturn("refresh-token");

        var response = authService.loginWithKakao("code", "https://front/callback");

        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.isNewUser()).isTrue();
        assertThat(response.user().userId()).isEqualTo(12);
        verify(refreshTokenRepository).issue(12);
    }

    @Test
    void withdrawnKakaoIdentityCannotLoginAgain() {
        when(kakaoOauthClient.authorize("code", "https://front/callback"))
                .thenReturn(authorization());
        when(userService.loginOrRegister(any())).thenReturn(UserLoginResult.rejectedWithdrawnUser());

        assertThatThrownBy(() -> authService.loginWithKakao("code", "https://front/callback"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.KAKAO_AUTH_FAILED));
    }

    @Test
    void reusedRefreshTokenIsRejected() {
        when(refreshTokenRepository.rotate("old"))
                .thenReturn(new RefreshTokenRepository.RotationResult(false, null, null));

        assertThatThrownBy(() -> authService.refresh("old"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID));
    }

    private KakaoAuthorization authorization() {
        return new KakaoAuthorization(
                123L,
                "김미미",
                null,
                null,
                false,
                null,
                Instant.parse("2026-09-18T00:00:00Z"),
                "kakao-refresh",
                5_184_000L);
    }
}
