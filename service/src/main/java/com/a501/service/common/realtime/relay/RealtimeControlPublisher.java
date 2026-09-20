package com.a501.service.common.realtime.relay;

import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RealtimeControlPublisher {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RealtimeControlPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void closeDuplicate(String connectionId) {
        publish(new RealtimeControlMessage(RealtimeControlAction.CLOSE_DUPLICATE, connectionId));
    }

    private void publish(RealtimeControlMessage message) {
        try {
            redisTemplate.convertAndSend(
                    RealtimeRelayTopic.CONTROL, RealtimeRedisMessages.write(objectMapper, message));
        } catch (DataAccessException exception) {
            // 중계와 달리 알리지 않는다. 지시가 닿지 않아도 새 연결은 멀쩡하고, 남은 옛
            // 연결은 heartbeat 가 결국 걷어 간다.
            log.warn("realtime 지시 전달 실패: action={}", message.action(), exception);
        }
    }
}
