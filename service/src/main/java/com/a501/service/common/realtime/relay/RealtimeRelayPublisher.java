package com.a501.service.common.realtime.relay;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeErrorCode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 보낸 pod 이라고 해서 자기 연결에 직접 넣지 않는다. 넣는 길이 둘이면 같은 pod 에 있는
 * 사람만 순서가 달라지거나 두 번 받는다.
 */
@Slf4j
@Component
public class RealtimeRelayPublisher {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RealtimeRelayPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(RealtimeEnvelope envelope) {
        publish(envelope, null);
    }

    public void publish(RealtimeEnvelope envelope, String excludeConnectionId) {
        String topic = RealtimeRelayTopic.of(envelope.channel(), envelope.channelId());
        String body =
                RealtimeRedisMessages.write(
                        objectMapper, new RealtimeRelayMessage(envelope, excludeConnectionId));
        try {
            redisTemplate.convertAndSend(topic, body);
        } catch (DataAccessException exception) {
            // Redis 가 없으면 다른 pod 의 상대에게 닿을 길이 없다. 보낸 사람이 알아야 한다.
            log.warn("realtime 중계 실패: topic={} type={}", topic, envelope.type(), exception);
            throw new BusinessException(RealtimeErrorCode.RELAY_UNAVAILABLE);
        }
    }
}
