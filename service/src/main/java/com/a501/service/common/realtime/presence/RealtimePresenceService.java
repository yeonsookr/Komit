package com.a501.service.common.realtime.presence;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.a501.service.common.realtime.RealtimePrincipal;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.config.RealtimeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 채널에 누가 들어와 있는지 pod 를 건너 공유한다.
 *
 * <p>점수를 만료 시각으로 쓴다. pod 가 갑자기 죽어 정리하지 못한 항목도 읽을 때 걸러지므로,
 * 남은 흔적이 영원히 들어와 있는 사람으로 보이지 않는다.
 */
@Slf4j
@Service
public class RealtimePresenceService {
    private static final String PRESENCE_KEY_PREFIX = "realtime:presence:";
    private static final String OWNER_KEY_PREFIX = "realtime:owner:";
    private static final String SEPARATOR = ":";

    /**
     * 대표인 연결만 기록과 수명을 연장한다. 소유권 확인과 갱신을 나누면 사이에 새 연결이
     * 대표를 가져가도 예전 연결이 다시 TTL을 늘릴 수 있다.
     */
    private static final DefaultRedisScript<Long> TOUCH_SCRIPT = new DefaultRedisScript<>("""
            local owner = redis.call('GET', KEYS[2])
            if not owner then
              redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[4])
            elseif owner ~= ARGV[1] then
              return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[3], ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            redis.call('PEXPIRE', KEYS[2], ARGV[4])
            return 1
            """, Long.class);

    /** 읽기와 쓰기가 한 명령이라 두 탭이 같은 순간에 들어와도 마지막 하나만 대표로 남는다. */
    private static final DefaultRedisScript<String> CLAIM_SCRIPT = new DefaultRedisScript<>("""
            local previous = redis.call('GET', KEYS[1])
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            return previous
            """, String.class);

    /** 내가 대표일 때만 대표 자리를 비운다. 늦게 닫히는 옛 연결이 새 연결의 자리를 지우면 안 된다. */
    private static final DefaultRedisScript<Long> LEAVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREM', KEYS[1], ARGV[1])
            if redis.call('GET', KEYS[2]) == ARGV[2] then
              redis.call('DEL', KEYS[2])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public RealtimePresenceService(
            StringRedisTemplate redisTemplate, RealtimeProperties properties) {
        this(redisTemplate, properties, Clock.systemUTC());
    }

    RealtimePresenceService(
            StringRedisTemplate redisTemplate, RealtimeProperties properties, Clock clock) {
        this.redisTemplate = redisTemplate;
        // 조용한 연결이 heartbeat 에 끊기는 시점과 presence 에서 사라지는 시점을 맞춘다.
        this.ttl = properties.heartbeatTimeout();
        this.clock = clock;
    }

    public void touch(RealtimePrincipal principal) {
        touchAll(List.of(principal));
    }

    /**
     * 한 건의 Redis 접속이 실패해도 나머지는 계속 알린다. 다른 연결에게 대표 자리를 내준
     * 연결은 돌려줘 heartbeat가 로컬 연결을 접을 수 있게 한다.
     */
    public List<RealtimePrincipal> touchAll(Collection<RealtimePrincipal> principals) {
        if (principals.isEmpty()) {
            return List.of();
        }
        String expiresAt = String.valueOf(clock.millis() + ttl.toMillis());
        String ttlMillis = String.valueOf(ttl.toMillis());
        List<RealtimePrincipal> noLongerOwners = new java.util.ArrayList<>();
        for (RealtimePrincipal principal : principals) {
            try {
                Long touched = redisTemplate.execute(
                        TOUCH_SCRIPT,
                        keys(principal),
                        principal.connectionId(),
                        member(principal),
                        expiresAt,
                        ttlMillis);
                if (Long.valueOf(0L).equals(touched)) {
                    noLongerOwners.add(principal);
                }
            } catch (DataAccessException exception) {
                // presence 는 장부일 뿐 메시지가 오가는 길이 아니다. 던지면 연결이 끊긴다.
                log.warn("realtime presence 기록 실패: userId={}", principal.userId(), exception);
            }
        }
        return List.copyOf(noLongerOwners);
    }

    /** 대표 연결을 나로 바꾸고 직전 대표를 돌려준다. 없거나 나 자신이면 비어 있다. */
    public Optional<String> claimOwnership(RealtimePrincipal principal) {
        try {
            String previous =
                    redisTemplate.execute(
                            CLAIM_SCRIPT,
                            List.of(ownerKey(principal)),
                            principal.connectionId(),
                            String.valueOf(ttl.toMillis()));
            if (previous == null || previous.equals(principal.connectionId())) {
                return Optional.empty();
            }
            return Optional.of(previous);
        } catch (DataAccessException exception) {
            throw new OwnershipClaimException(principal, exception);
        }
    }

    public static final class OwnershipClaimException extends RuntimeException {
        public OwnershipClaimException(RealtimePrincipal principal, DataAccessException cause) {
            super("대표 연결을 안전하게 정할 수 없습니다: userId=" + principal.userId(), cause);
        }
    }

    public void leave(RealtimePrincipal principal) {
        try {
            redisTemplate.execute(
                    LEAVE_SCRIPT, keys(principal), member(principal), principal.connectionId());
        } catch (DataAccessException exception) {
            log.warn("realtime presence 삭제 실패: userId={}", principal.userId(), exception);
        }
    }

    /**
     * 만료된 항목은 읽으면서 치운다.
     *
     * <p>순서에 뜻을 두지 않는다. 점수는 마지막으로 알린 시각이고 pod 마다 시계가 다르므로,
     * 들어온 차례를 여기서 읽어 내면 안 된다. 차례가 필요하면 {@link #claimOwnership} 을 쓴다.
     */
    public List<RealtimePresence> inChannel(RealtimeChannelType channel, String channelId) {
        String key = presenceKey(channel, channelId);
        try {
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, clock.millis());
            Set<String> members = redisTemplate.opsForZSet().range(key, 0, -1);
            if (members == null) {
                return List.of();
            }
            return members.stream()
                    .map(RealtimePresenceService::parse)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (DataAccessException exception) {
            log.warn("realtime presence 조회 실패: key={}", key, exception);
            return List.of();
        }
    }

    private List<String> keys(RealtimePrincipal principal) {
        return List.of(
                presenceKey(principal.channel(), principal.channelId()), ownerKey(principal));
    }

    private String presenceKey(RealtimeChannelType channel, String channelId) {
        return PRESENCE_KEY_PREFIX + channel.name() + SEPARATOR + channelId;
    }

    private String ownerKey(RealtimePrincipal principal) {
        return OWNER_KEY_PREFIX
                + principal.channel().name()
                + SEPARATOR
                + principal.channelId()
                + SEPARATOR
                + principal.userId();
    }

    private String member(RealtimePrincipal principal) {
        return principal.userId() + SEPARATOR + principal.connectionId();
    }

    private static RealtimePresence parse(String member) {
        int separator = member.indexOf(SEPARATOR);
        if (separator <= 0 || separator == member.length() - 1) {
            return null;
        }
        try {
            return new RealtimePresence(
                    Integer.valueOf(member.substring(0, separator)),
                    member.substring(separator + 1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
