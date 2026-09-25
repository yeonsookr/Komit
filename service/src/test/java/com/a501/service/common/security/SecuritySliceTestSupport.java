package com.a501.service.common.security;

import java.time.Instant;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

/**
 * 컨트롤러 슬라이스 테스트가 함께 쓰는 토큰 생성 도우미입니다.
 * 시크릿은 각 테스트가 TestPropertySource로 주입하는 값과 같아야 합니다.
 */
public final class SecuritySliceTestSupport {
    public static final String USER_JWT_SECRET = "user-jwt-secret-for-tests-at-least-32-bytes";
    public static final String KAKAO_TOKEN_ENCRYPTION_KEY = "ZGV2LW9ubHkta2FrYW8tdG9rZW4ta2V5LTMyYnl0ZXM=";
    /** 전사를 받아 가는 노트북의 토큰. 사용자 JWT 가 아니다. */
    public static final String TRANSCRIBER_TOKEN = "transcriber-token-for-tests";

    private SecuritySliceTestSupport() {
    }

    /** 만료된 사용자 토큰. jwt-samples.md 검증 케이스 2번에 해당합니다. */
    public static String expiredToken(int userId) {
        return JWT.create()
                .withSubject(Integer.toString(userId))
                .withIssuedAt(Instant.now().minusSeconds(7_200))
                .withExpiresAt(Instant.now().minusSeconds(3_600))
                .sign(Algorithm.HMAC256(USER_JWT_SECRET));
    }

    /** 다른 키로 서명한 토큰. jwt-samples.md 검증 케이스 6번(운영자 토큰으로 사용자 영역 호출)에 해당합니다. */
    public static String tokenSignedWithOtherKey(int userId) {
        return JWT.create()
                .withSubject(Integer.toString(userId))
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plusSeconds(900))
                .sign(Algorithm.HMAC256("komit-admin-jwt-secret-dev-only-change-me"));
    }
}
