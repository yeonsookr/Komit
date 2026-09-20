package com.a501.service.common.realtime.relay;

import java.nio.charset.StandardCharsets;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/** 중계와 지시가 같은 규칙을 쓰도록 한곳에 둔다. */
@Slf4j
final class RealtimeRedisMessages {
    private RealtimeRedisMessages() {}

    static String write(ObjectMapper objectMapper, Object message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Realtime redis message is not serializable", exception);
        }
    }

    /** 읽지 못하면 null 을 준다. 배포가 겹쳐 모르는 형식이 와도 구독이 끊기지 않아야 한다. */
    static <T> T read(ObjectMapper objectMapper, byte[] body, Class<T> type) {
        try {
            return objectMapper.readValue(new String(body, StandardCharsets.UTF_8), type);
        } catch (Exception exception) {
            log.warn("realtime 메시지를 읽지 못해 버린다: type={}", type.getSimpleName(), exception);
            return null;
        }
    }
}
