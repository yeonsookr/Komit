package com.a501.service.common.realtime.ticket;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import com.a501.service.config.RealtimeProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealtimeTicketService {
    private static final String KEY_PREFIX = "realtime:ticket:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlMillis;

    public RealtimeTicketService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RealtimeProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlMillis = properties.ticketTtl().toMillis();
    }

    public String issue(RealtimeTicket ticket) {
        String value = UUID.randomUUID().toString();
        redisTemplate
                .opsForValue()
                .set(key(value), serialize(ticket), Duration.ofMillis(ttlMillis));
        return value;
    }

    // 읽기와 지우기가 한 명령(GETDEL)이라, 같은 티켓으로 동시에 두 번 연결해도 하나만 통과한다.
    public Optional<RealtimeTicket> consume(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String stored = redisTemplate.opsForValue().getAndDelete(key(value));
        if (stored == null) {
            return Optional.empty();
        }
        return deserialize(stored);
    }

    private String key(String value) {
        // 원본 티켓을 저장하지 않는다. Redis 를 들여다볼 수 있는 사람이 남의 연결을 열지 못한다.
        return KEY_PREFIX + hash(value);
    }

    private String serialize(RealtimeTicket ticket) {
        try {
            return objectMapper.writeValueAsString(ticket);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Realtime ticket is not serializable", exception);
        }
    }

    private Optional<RealtimeTicket> deserialize(String stored) {
        try {
            return Optional.of(objectMapper.readValue(stored, RealtimeTicket.class));
        } catch (JsonProcessingException exception) {
            // 형식이 바뀐 뒤 남아 있던 값. 이미 지워졌으므로 없는 티켓과 같이 다룬다.
            return Optional.empty();
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
