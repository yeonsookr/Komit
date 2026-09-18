package com.a501.service.common.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import com.a501.service.config.AuthProperties;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class JwtTokenProvider {
    private final AuthProperties properties;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final Clock clock;

    @Autowired
    public JwtTokenProvider(AuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtTokenProvider(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.algorithm = Algorithm.HMAC256(properties.userJwtSecret());
        this.verifier = JWT.require(algorithm).build();
        this.clock = clock;
    }

    public String issue(Integer userId) {
        Instant issuedAt = clock.instant();
        return JWT.create()
                .withSubject(userId.toString())
                .withIssuedAt(Date.from(issuedAt))
                .withExpiresAt(Date.from(issuedAt.plus(properties.accessTokenTtl())))
                .sign(algorithm);
    }

    public Integer verifyAndGetUserId(String token) {
        try {
            String subject = verifier.verify(token).getSubject();
            int userId = Integer.parseInt(subject);
            if (userId <= 0) {
                throw new JWTVerificationException("Invalid subject");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new JWTVerificationException("Invalid subject", exception);
        }
    }

    public long expiresInSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}
