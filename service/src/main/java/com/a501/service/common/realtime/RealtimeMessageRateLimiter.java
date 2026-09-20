package com.a501.service.common.realtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import com.a501.service.config.RealtimeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 연결 하나가 짧은 메시지를 반복해 JSON 파싱, handler와 Redis를 독점하지 못하게 한다. */
@Component
public class RealtimeMessageRateLimiter {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final double refillPerNano;
    private final int capacity;
    private final int violationsBeforeClose;
    private final LongSupplier nanoTime;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public RealtimeMessageRateLimiter(RealtimeProperties properties) {
        this(properties, System::nanoTime);
    }

    RealtimeMessageRateLimiter(RealtimeProperties properties, LongSupplier nanoTime) {
        this.refillPerNano = (double) properties.messagesPerSecond() / NANOS_PER_SECOND;
        this.capacity = properties.messageBurst();
        this.violationsBeforeClose = properties.rateLimitViolationsBeforeClose();
        this.nanoTime = nanoTime;
    }

    public Decision acquire(String connectionId) {
        long now = nanoTime.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(connectionId, ignored -> new Bucket(capacity, now));
        return bucket.acquire(now);
    }

    public void remove(String connectionId) {
        buckets.remove(connectionId);
    }

    int size() {
        return buckets.size();
    }

    public record Decision(boolean allowed, boolean close) {
        private static final Decision ALLOWED = new Decision(true, false);
    }

    private final class Bucket {
        private double tokens;
        private long refilledAt;
        private int consecutiveViolations;

        private Bucket(double tokens, long refilledAt) {
            this.tokens = tokens;
            this.refilledAt = refilledAt;
        }

        private synchronized Decision acquire(long now) {
            long elapsed = Math.max(0L, now - refilledAt);
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + (elapsed * refillPerNano));
                refilledAt = now;
            }

            if (tokens >= 1.0d) {
                tokens -= 1.0d;
                consecutiveViolations = 0;
                return Decision.ALLOWED;
            }

            consecutiveViolations++;
            return new Decision(false, consecutiveViolations >= violationsBeforeClose);
        }
    }
}
