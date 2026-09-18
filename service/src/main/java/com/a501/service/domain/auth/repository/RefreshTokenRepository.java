package com.a501.service.domain.auth.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.a501.service.config.AuthProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepository {
    private static final String USER_KEY_PREFIX = "auth:refresh:user:";
    private static final String TOKEN_KEY_PREFIX = "auth:refresh:token:";

    private static final DefaultRedisScript<Long> ISSUE_SCRIPT = new DefaultRedisScript<>("""
            local previous = redis.call('GET', KEYS[1])
            if previous then
              redis.call('DEL', ARGV[3] .. previous)
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[4])
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[4])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            if redis.call('GET', KEYS[2]) ~= ARGV[2] then
              return 0
            end
            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[4])
            redis.call('SET', KEYS[3], ARGV[1], 'PX', ARGV[4])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> DELETE_SCRIPT = new DefaultRedisScript<>("""
            local tokenHash = redis.call('GET', KEYS[1])
            if tokenHash then
              redis.call('DEL', ARGV[1] .. tokenHash)
            end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long ttlMillis;

    public RefreshTokenRepository(StringRedisTemplate redisTemplate, AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.ttlMillis = properties.refreshTokenTtl().toMillis();
    }

    public String issue(Integer userId) {
        String token = UUID.randomUUID().toString();
        String tokenHash = hash(token);
        redisTemplate.execute(
                ISSUE_SCRIPT,
                List.of(userKey(userId), tokenKey(tokenHash)),
                tokenHash,
                userId.toString(),
                TOKEN_KEY_PREFIX,
                Long.toString(ttlMillis));
        return token;
    }

    public RotationResult rotate(String currentToken) {
        String currentHash = hash(currentToken);
        String userIdValue = redisTemplate.opsForValue().get(tokenKey(currentHash));
        if (userIdValue == null) {
            return RotationResult.invalid();
        }

        Integer userId;
        try {
            userId = Integer.valueOf(userIdValue);
        } catch (NumberFormatException exception) {
            return RotationResult.invalid();
        }

        String nextToken = UUID.randomUUID().toString();
        String nextHash = hash(nextToken);
        Long rotated = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(tokenKey(currentHash), userKey(userId), tokenKey(nextHash)),
                userId.toString(),
                currentHash,
                nextHash,
                Long.toString(ttlMillis));
        if (!Long.valueOf(1).equals(rotated)) {
            return RotationResult.invalid();
        }
        return RotationResult.rotated(userId, nextToken);
    }

    public void deleteByUserId(Integer userId) {
        redisTemplate.execute(
                DELETE_SCRIPT,
                List.of(userKey(userId)),
                TOKEN_KEY_PREFIX);
    }

    private String userKey(Integer userId) {
        return USER_KEY_PREFIX + userId;
    }

    private String tokenKey(String tokenHash) {
        return TOKEN_KEY_PREFIX + tokenHash;
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record RotationResult(boolean valid, Integer userId, String refreshToken) {
        static RotationResult invalid() {
            return new RotationResult(false, null, null);
        }

        static RotationResult rotated(Integer userId, String refreshToken) {
            return new RotationResult(true, userId, refreshToken);
        }
    }
}
