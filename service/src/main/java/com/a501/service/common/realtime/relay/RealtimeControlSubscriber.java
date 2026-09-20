package com.a501.service.common.realtime.relay;

import com.a501.service.common.realtime.RealtimeConnection;
import com.a501.service.common.realtime.RealtimeConnectionRegistry;
import com.a501.service.common.realtime.RealtimePrincipal;
import com.a501.service.common.realtime.protocol.RealtimeCloseStatus;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeSystemEvent;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RealtimeControlSubscriber implements MessageListener {
    private final RealtimeConnectionRegistry registry;
    private final ObjectMapper objectMapper;

    public RealtimeControlSubscriber(
            RealtimeConnectionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        RealtimeControlMessage control =
                RealtimeRedisMessages.read(
                        objectMapper, message.getBody(), RealtimeControlMessage.class);
        if (control == null || control.action() != RealtimeControlAction.CLOSE_DUPLICATE) {
            return;
        }

        RealtimeConnection connection = registry.find(control.connectionId()).orElse(null);
        if (connection == null) {
            return;
        }

        // 닫는 코드는 믿을 수 없다. 보낼 것이 쌓인 연결에는 감싼 session 이 자기 코드를 쓰고
        // 아예 닫히지 않기도 하므로, 이유를 먼저 보낸다.
        connection.send(replaced(connection.principal()));
        registry.disconnect(connection, RealtimeCloseStatus.DUPLICATE_CONNECTION);
    }

    private RealtimeEnvelope replaced(RealtimePrincipal principal) {
        return new RealtimeEnvelope(
                RealtimeEnvelope.CURRENT_VERSION,
                RealtimeSystemEvent.SYSTEM_REPLACED.name(),
                null,
                principal.channel(),
                principal.channelId(),
                null);
    }
}
