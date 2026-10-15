package com.a501.service.domain.settlement.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 한 계약의 지급을 한 번에 한 곳에서만 하게 막는 짧은 잠금.
 *
 * <p>서버는 여러 대가 동시에 돌고 지급 작업도 모든 대에서 깬다. 같은 계약을 두 대가 동시에 부르면
 * Escrow 가 같은 멱등키 요청을 겹쳐 처리하다 송금을 두 번 시도할 수 있다. JVM 잠금으로는 대 사이를
 * 막지 못하므로 모든 대가 보는 Redis 에 둔다. (BACKEND_CONVENTIONS 6.6)
 *
 * <p>잠금은 수명이 있다. 잡은 대가 죽어도 수명이 지나면 풀리고, 다음 회차가 같은 멱등키로 잇는다.
 * 수명은 지급 한 번(조회 둘 + 지급 하나)의 읽기 한도 합보다 길어야 한다.
 */
@Component
public class PayoutLock {
    private static final String KEY_PREFIX = "settlement:payout:contract:";
    /** 내가 잡은 잠금만 푼다. 수명이 지나 다른 대가 새로 잡은 잠금을 지우지 않기 위해서다. */
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public PayoutLock(StringRedisTemplate redis, @Value("${app.settlement.payout-lock-ttl}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    /**
     * 잠금을 잡는다. 다른 곳이 이미 잡고 있으면 비어 있다.
     *
     * @return 풀 때 돌려줄 표. 이 표를 가진 쪽만 풀 수 있다
     */
    public Optional<String> tryAcquire(Integer contractId) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key(contractId), token, ttl);
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    /** 잡은 잠금을 푼다. 이미 수명이 지나 남의 것이 됐으면 건드리지 않는다. */
    public void release(Integer contractId, String token) {
        redis.execute(RELEASE, List.of(key(contractId)), token);
    }

    private static String key(Integer contractId) {
        return KEY_PREFIX + contractId;
    }
}
