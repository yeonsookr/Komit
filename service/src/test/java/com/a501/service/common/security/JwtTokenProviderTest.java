package com.a501.service.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.a501.service.config.AuthProperties;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    private final AuthProperties properties = new AuthProperties(
            "user-jwt-secret-for-tests-at-least-32-bytes",
            "ZGV2LW9ubHkta2FrYW8tdG9rZW4ta2V5LTMyYnl0ZXM=",
            Duration.ofMinutes(15),
            Duration.ofDays(14));

    @Test
    void issuesOnlyContractClaimsForFifteenMinutes() {
        JwtTokenProvider provider = new JwtTokenProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC));

        String token = provider.issue(12);
        var decoded = JWT.decode(token);

        assertThat(decoded.getClaims()).containsOnlyKeys("sub", "iat", "exp");
        assertThat(decoded.getSubject()).isEqualTo("12");
        assertThat(decoded.getIssuedAtAsInstant()).isEqualTo(NOW);
        assertThat(decoded.getExpiresAtAsInstant()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(provider.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtTokenProvider provider = new JwtTokenProvider(properties);
        String forged = JWT.create()
                .withSubject("12")
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plusSeconds(900))
                .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256("different-secret"));

        assertThatThrownBy(() -> provider.verifyAndGetUserId(forged))
                .isInstanceOf(JWTVerificationException.class);
    }
}
