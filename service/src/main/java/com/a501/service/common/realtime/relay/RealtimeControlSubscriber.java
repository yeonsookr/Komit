package com.a501.service.common.realtime.relay;

import com.a501.service.common.realtime.RealtimeConnection;
import com.a501.service.common.realtime.RealtimeConnectionRegistry;
import com.a501.service.common.realtime.RealtimeDuplicateConnections;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RealtimeControlSubscriber implements MessageListener {
    private final RealtimeConnectionRegistry registry;
    private final RealtimeDuplicateConnections duplicates;
    private final ObjectMapper objectMapper;

    public RealtimeControlSubscriber(
            RealtimeConnectionRegistry registry,
            RealtimeDuplicateConnections duplicates,
            ObjectMapper objectMapper) {
        this.registry = registry;
        this.duplicates = duplicates;
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

        duplicates.replace(connection);
    }
}
