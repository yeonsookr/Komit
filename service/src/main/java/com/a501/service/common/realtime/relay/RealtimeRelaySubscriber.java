package com.a501.service.common.realtime.relay;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.RealtimeConnection;
import com.a501.service.common.realtime.RealtimeConnectionRegistry;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/** 다른 pod 에 붙은 연결은 그쪽이 맡는다. */
@Slf4j
@Component
public class RealtimeRelaySubscriber implements MessageListener {
    private final RealtimeConnectionRegistry registry;
    private final ObjectMapper objectMapper;

    public RealtimeRelaySubscriber(
            RealtimeConnectionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        RealtimeRelayMessage relayed =
                RealtimeRedisMessages.read(
                        objectMapper, message.getBody(), RealtimeRelayMessage.class);
        RealtimeEnvelope envelope = validated(relayed);
        if (envelope == null) {
            return;
        }

        for (RealtimeConnection connection :
                registry.inChannel(envelope.channel(), envelope.channelId())) {
            if (connection.connectionId().equals(relayed.excludeConnectionId())) {
                continue;
            }
            connection.send(envelope);
        }
    }

    /**
     * 형식만 맞고 channel 이 비어 있으면 뒤에서 터지는데, 그 예외는 읽기 실패와 달리 걸러지지
     * 않아 구독 전체가 오류로 물든다.
     */
    private RealtimeEnvelope validated(RealtimeRelayMessage relayed) {
        if (relayed == null || relayed.envelope() == null) {
            return null;
        }
        try {
            return RealtimeEnvelope.validated(relayed.envelope());
        } catch (BusinessException exception) {
            log.warn("realtime 중계 봉투가 규약에 맞지 않아 버린다: code={}",
                    exception.getErrorCode().code());
            return null;
        }
    }
}
