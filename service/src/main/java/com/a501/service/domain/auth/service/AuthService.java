package com.a501.service.domain.auth.service;

import java.time.Clock;
import java.time.Instant;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.domain.auth.AuthErrorCode;
import com.a501.service.domain.auth.client.KakaoAuthorization;
import com.a501.service.domain.auth.client.KakaoOauthClient;
import com.a501.service.domain.auth.dto.response.KakaoLoginResponse;
import com.a501.service.domain.auth.dto.response.LoginUserResponse;
import com.a501.service.domain.auth.dto.response.TokenResponse;
import com.a501.service.domain.auth.repository.RefreshTokenRepository;
import com.a501.service.domain.user.dto.KakaoUserData;
import com.a501.service.domain.user.dto.UserLoginResult;
import com.a501.service.domain.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 로그인·재발급·로그아웃 유스케이스입니다.
 *
 * 이 클래스에는 {@code @Transactional}을 걸지 않습니다. 카카오 외부 호출과 Redis만 직접 다루고,
 * DB 변경은 {@link UserService#loginOrRegister}가 자기 트랜잭션에서 처리합니다. 여기에 트랜잭션을 열면
 * 카카오 호출 동안 DB 커넥션을 붙잡고(BACKEND_CONVENTIONS 16), readOnly로 열면 안쪽 쓰기 트랜잭션이
 * 합류해 읽기 DB로 라우팅됩니다.
 */
@Service
public class AuthService {
    private final KakaoOauthClient kakaoOauthClient;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    @Autowired
    public AuthService(
            KakaoOauthClient kakaoOauthClient,
            UserService userService,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository) {
        this(kakaoOauthClient, userService, jwtTokenProvider, refreshTokenRepository, Clock.systemUTC());
    }

    AuthService(
            KakaoOauthClient kakaoOauthClient,
            UserService userService,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository,
            Clock clock) {
        this.kakaoOauthClient = kakaoOauthClient;
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    public KakaoLoginResponse loginWithKakao(String code, String redirectUri) {
        KakaoAuthorization authorization = kakaoOauthClient.authorize(code, redirectUri);
        Instant refreshExpiresAt = clock.instant().plusSeconds(authorization.refreshTokenExpiresInSeconds());
        UserLoginResult login = userService.loginOrRegister(new KakaoUserData(
                authorization.kakaoId(),
                authorization.nickname(),
                authorization.profileImageUrl(),
                authorization.email(),
                authorization.emailVerified(),
                authorization.emailValid(),
                authorization.connectedAt(),
                authorization.refreshToken(),
                refreshExpiresAt));
        if (login.withdrawn()) {
            throw new BusinessException(AuthErrorCode.KAKAO_AUTH_FAILED);
        }

        Integer userId = login.user().userId();
        String accessToken = jwtTokenProvider.issue(userId);
        String refreshToken = refreshTokenRepository.issue(userId);
        return new KakaoLoginResponse(
                accessToken,
                refreshToken,
                jwtTokenProvider.expiresInSeconds(),
                login.newUser(),
                LoginUserResponse.from(login.user()));
    }

    public TokenResponse refresh(String refreshToken) {
        RefreshTokenRepository.RotationResult rotation = refreshTokenRepository.rotate(refreshToken);
        if (!rotation.valid()) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!userService.isActive(rotation.userId())) {
            refreshTokenRepository.deleteByUserId(rotation.userId());
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        return new TokenResponse(
                jwtTokenProvider.issue(rotation.userId()),
                rotation.refreshToken(),
                jwtTokenProvider.expiresInSeconds());
    }

    public void logout(Integer userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
