package com.a501.service.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.a501.service.config.RealtimeProperties;
import org.junit.jupiter.api.Test;

class RealtimeMessageRateLimiterTest {
    private final AtomicLong now = new AtomicLong();

    @Test
    void 순간_허용량까지는_연속으로_받는다() {
        RealtimeMessageRateLimiter limiter = limiter(2, 3, 2);

        assertThat(limiter.acquire("conn").allowed()).isTrue();
        assertThat(limiter.acquire("conn").allowed()).isTrue();
        assertThat(limiter.acquire("conn").allowed()).isTrue();
        assertThat(limiter.acquire("conn").allowed()).isFalse();
    }

    @Test
    void 시간이_지나면_초당_설정량만큼_다시_받는다() {
        RealtimeMessageRateLimiter limiter = limiter(2, 2, 2);
        limiter.acquire("conn");
        limiter.acquire("conn");

        now.addAndGet(Duration.ofMillis(500).toNanos());

        assertThat(limiter.acquire("conn").allowed()).isTrue();
        assertThat(limiter.acquire("conn").allowed()).isFalse();
    }

    @Test
    void 연속_초과가_설정한_횟수에_닿으면_닫으라고_알린다() {
        RealtimeMessageRateLimiter limiter = limiter(1, 1, 2);
        limiter.acquire("conn");

        RealtimeMessageRateLimiter.Decision first = limiter.acquire("conn");
        RealtimeMessageRateLimiter.Decision second = limiter.acquire("conn");

        assertThat(first.close()).isFalse();
        assertThat(second.close()).isTrue();
    }

    @Test
    void 연결을_지우면_제한_상태도_함께_지운다() {
        RealtimeMessageRateLimiter limiter = limiter(1, 1, 2);
        limiter.acquire("conn");
        assertThat(limiter.acquire("conn").allowed()).isFalse();

        limiter.remove("conn");

        assertThat(limiter.size()).isZero();
        assertThat(limiter.acquire("conn").allowed()).isTrue();
    }

    private RealtimeMessageRateLimiter limiter(int perSecond, int burst, int violations) {
        RealtimeProperties properties = new RealtimeProperties(
                Duration.ofSeconds(30),
                65536,
                perSecond,
                burst,
                violations,
                Duration.ofSeconds(10),
                524288,
                Duration.ofSeconds(20),
                Duration.ofSeconds(60),
                List.of());
        return new RealtimeMessageRateLimiter(properties, now::get);
    }
}
