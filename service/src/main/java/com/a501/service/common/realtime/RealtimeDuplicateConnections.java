package com.a501.service.common.realtime;

import com.a501.service.common.realtime.presence.RealtimePresenceService;
import com.a501.service.common.realtime.relay.RealtimeControlPublisher;
import org.springframework.stereotype.Component;

/**
 * 같은 사람이 같은 채널에 또 들어오면 앞의 연결을 접는다. 2인 통화에서 한 사람이 두 자리를
 * 차지하면 상대에게 상대가 둘로 보여, 어느 쪽과 협상할지 정할 수 없다.
 */
@Component
public class RealtimeDuplicateConnections {
    private final RealtimePresenceService presence;
    private final RealtimeControlPublisher controlPublisher;

    public RealtimeDuplicateConnections(
            RealtimePresenceService presence, RealtimeControlPublisher controlPublisher) {
        this.presence = presence;
        this.controlPublisher = controlPublisher;
    }

    /**
     * 누가 먼저 들어왔는지 시각으로 견주지 않는다. 시각은 pod 마다 다르고 살아 있다고 알릴
     * 때마다 덮어써져, 늦게 온 쪽이 앞선 것처럼 보인다. 그러면 두 탭이 서로를 접거나 아무도
     * 접지 못한다. 자리를 바꾸는 일을 Redis 가 한 명령으로 하게 해 대상을 하나로 정한다.
     */
    public void closeOlderOf(RealtimePrincipal joined) {
        presence.claimOwnership(joined).ifPresent(controlPublisher::closeDuplicate);
    }
}
