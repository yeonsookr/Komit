package com.a501.service.common.realtime;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.presence.RealtimePresence;
import com.a501.service.common.realtime.presence.RealtimePresenceService;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeSystemEvent;
import com.a501.service.common.realtime.relay.RealtimeRelayPublisher;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 채널에 사람이 붙고 떨어진 것을 같은 채널의 나머지에게 알린다.
 *
 * <p>이 알림이 없으면 상대가 탭을 닫거나 회선이 끊겼을 때 남은 사람은 아무것도 모른다. 영상은
 * 브라우저끼리 직접 오가므로 화면은 얼어붙은 그림을 한참 붙들고 있다가, ICE 가 죽고 나서야
 * 무언가 잘못됐다는 것을 알아챈다.
 */
@Slf4j
@Component
public class RealtimePeerNotices {
    private final RealtimePresenceService presence;
    private final RealtimeRelayPublisher relayPublisher;
    private final ObjectMapper objectMapper;

    public RealtimePeerNotices(
            RealtimePresenceService presence,
            RealtimeRelayPublisher relayPublisher,
            ObjectMapper objectMapper) {
        this.presence = presence;
        this.relayPublisher = relayPublisher;
        this.objectMapper = objectMapper;
    }

    public void joined(RealtimePrincipal principal) {
        publish(principal, RealtimeSystemEvent.SYSTEM_PEER_JOINED);
    }

    /**
     * 그 사람의 연결이 하나도 남지 않았을 때만 알린다.
     *
     * <p>탭을 새로 열면 먼저 있던 연결이 접히는데, 그것까지 알리면 상대는 사람이 나간 것으로
     * 읽는다. 장부에 그 사람이 남아 있는지로 가른다.
     */
    public void leftIfGone(RealtimePrincipal principal) {
        if (stillHere(principal)) {
            return;
        }
        publish(principal, RealtimeSystemEvent.SYSTEM_PEER_LEFT);
    }

    private boolean stillHere(RealtimePrincipal principal) {
        return presence.inChannel(principal.channel(), principal.channelId()).stream()
                .map(RealtimePresence::userId)
                .anyMatch(userId -> Objects.equals(userId, principal.userId()));
    }

    private void publish(RealtimePrincipal principal, RealtimeSystemEvent event) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("userId", principal.userId());

        try {
            relayPublisher.publish(
                    new RealtimeEnvelope(
                            RealtimeEnvelope.CURRENT_VERSION,
                            event.name(),
                            null,
                            principal.channel(),
                            principal.channelId(),
                            payload),
                    principal.connectionId());
        } catch (BusinessException exception) {
            // 알리지 못해도 연결을 끊지는 않는다. 상대는 ICE 가 죽는 것으로 뒤늦게 알아챈다.
            log.warn(
                    "realtime 참가 알림 실패: event={} userId={}",
                    event,
                    principal.userId(),
                    exception);
        }
    }
}
