package com.a501.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 격리된 Redis 컨테이너에 실제로 붙어 refresh token 저장 방식(TTL이 있는 문자열 키)이 동작하는지 확인합니다.
 * 별도 test 프로필 파일 없이 {@code @ServiceConnection}이 컨테이너 주소를 주입합니다.
 */
@Testcontainers
@DataRedisTest
class RedisConnectionIntegrationTest {

    // 운영 Redis(8 계열)와 같은 메이저 버전의 공식 이미지를 씁니다.
    // GenericContainer는 이미지 종류를 알 수 없어 name으로 Redis임을 알려 줍니다.
    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("TTL을 단 키를 저장하면 같은 값이 읽히고 남은 TTL이 보인다")
    void setWithTtlThenGet() {
        String key = "test:refresh-token:" + UUID.randomUUID();

        redisTemplate.opsForValue().set(key, "token-value", Duration.ofSeconds(30));

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("token-value");
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 30L);
    }

    @Test
    @DisplayName("키를 지우면 다시 읽히지 않는다")
    void deleteThenMissing() {
        String key = "test:refresh-token:" + UUID.randomUUID();
        redisTemplate.opsForValue().set(key, "token-value", Duration.ofSeconds(30));

        assertThat(redisTemplate.delete(key)).isTrue();

        assertThat(redisTemplate.hasKey(key)).isFalse();
        assertThat(redisTemplate.opsForValue().get(key)).isNull();
    }
}
