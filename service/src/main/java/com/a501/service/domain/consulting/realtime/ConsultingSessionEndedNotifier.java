package com.a501.service.domain.consulting.realtime;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeSystemEvent;
import com.a501.service.common.realtime.relay.RealtimeRelayPublisher;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 세션이 끝난 것을 그 세션 채널의 양쪽에 알린다.
 *
 * <p>이 알림이 없으면 끝낸 사람만 녹음을 마감한다. 상대 화면에는 통화가 끊긴 것으로만 보여,
 * 자기도 종료 버튼을 누르기 전까지 녹음이 올라가지 않는다.
 *
 * <p><b>종료가 DB 에 기록된 뒤에 보낸다.</b> 커밋 전에 보내면 상대가 받자마자 녹음을 올리는데
 * 그때 서버는 아직 세션이 끝나지 않은 것으로 보고 거절한다.
 *
 * <p>끝낸 사람도 함께 받는다. 그쪽은 HTTP 응답으로 이미 알지만, 그 응답을 받지 못했을 때
 * 이 쪽지가 남는다.
 */
@Slf4j
@Component
public class ConsultingSessionEndedNotifier {
    private final RealtimeRelayPublisher relayPublisher;
    private final ObjectMapper objectMapper;

    public ConsultingSessionEndedNotifier(
            RealtimeRelayPublisher relayPublisher, ObjectMapper objectMapper) {
        this.relayPublisher = relayPublisher;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener
    public void onSessionEnded(ConsultingSessionEnded event) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("endedAt", event.endedAt().atOffset(ZoneOffset.UTC).toString());

        try {
            relayPublisher.publish(
                    new RealtimeEnvelope(
                            RealtimeEnvelope.CURRENT_VERSION,
                            RealtimeSystemEvent.SYSTEM_SESSION_ENDED.name(),
                            null,
                            RealtimeChannelType.CONSULTING_SESSION,
                            String.valueOf(event.sessionId()),
                            payload));
        } catch (BusinessException exception) {
            // 알리지 못해도 종료는 이미 기록됐다. 화면은 세션을 다시 물어 끝난 것을 알아챈다.
            log.warn("세션 종료 알림 실패: sessionId={}", event.sessionId(), exception);
        }
    }
}
