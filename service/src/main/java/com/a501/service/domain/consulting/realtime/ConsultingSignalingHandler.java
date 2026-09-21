package com.a501.service.domain.consulting.realtime;

import com.a501.service.common.realtime.RealtimeDomainHandler;
import com.a501.service.common.realtime.RealtimePrincipal;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;
import com.a501.service.common.realtime.relay.RealtimeRelayPublisher;
import org.springframework.stereotype.Component;

/**
 * 통화 쪽지를 같은 세션의 상대에게 넘깁니다.
 *
 * <p>내용은 열어 보지 않습니다. 두 사람이 무엇을 주고받는지는 브라우저끼리의 약속이고,
 * 서버가 하는 일은 <b>다른 pod 에 붙은 상대에게 닿게 하는 것</b>뿐입니다.
 *
 * <p>보낸 사람 자신에게는 돌려주지 않습니다. 연결 식별자를 빼고 중계하므로 같은 pod 에 있든
 * 다른 pod 에 있든 받는 사람이 같습니다.
 */
@Component
public class ConsultingSignalingHandler implements RealtimeDomainHandler {
    private final RealtimeRelayPublisher relayPublisher;

    public ConsultingSignalingHandler(RealtimeRelayPublisher relayPublisher) {
        this.relayPublisher = relayPublisher;
    }

    @Override
    public RealtimeEventFamily family() {
        return RealtimeEventFamily.RTC;
    }

    @Override
    public boolean supports(String type) {
        return ConsultingSignalEvent.isKnown(type);
    }

    @Override
    public void handle(RealtimePrincipal principal, RealtimeEnvelope envelope) {
        relayPublisher.publish(envelope, principal.connectionId());
    }
}
