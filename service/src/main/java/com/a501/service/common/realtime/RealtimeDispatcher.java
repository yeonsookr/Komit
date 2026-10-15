package com.a501.service.common.realtime;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeErrorCode;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RealtimeDispatcher {
    private final List<RealtimeDomainHandler> handlers;

    // 컬렉션을 그대로 받으면 Spring 이 후보 한 개 이상을 요구해, 아직 handler 가 없는
    // 동안에는 컨텍스트가 뜨지 않는다. ObjectProvider 는 비어 있어도 된다.
    @Autowired
    public RealtimeDispatcher(ObjectProvider<RealtimeDomainHandler> handlers) {
        this(handlers.stream().toList());
    }

    RealtimeDispatcher(List<RealtimeDomainHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    public void dispatch(RealtimePrincipal principal, RealtimeEnvelope raw) {
        RealtimeEnvelope envelope = RealtimeEnvelope.validated(raw);

        if (!principal.owns(envelope.channel(), envelope.channelId())) {
            throw new BusinessException(RealtimeErrorCode.CHANNEL_FORBIDDEN);
        }

        // 연결 확인과 공통 오류는 공통 계층이 소켓 경계에서 다룬다. 여기까지 내려왔다면
        // 그 경계가 빠진 것이므로, 클라이언트 오류로 위장하지 않고 서버 결함으로 드러낸다.
        if (envelope.family() == RealtimeEventFamily.SYSTEM) {
            throw new IllegalStateException(
                    "SYSTEM 이벤트는 domain handler 로 보내지 않는다: " + envelope.type());
        }

        if (!principal.allows(envelope.family())) {
            throw new BusinessException(RealtimeErrorCode.CHANNEL_FORBIDDEN);
        }

        handlers.stream()
                .filter(handler -> handler.family() == envelope.family())
                .filter(handler -> handler.supports(envelope.type()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(RealtimeErrorCode.UNKNOWN_EVENT_TYPE))
                .handle(principal, envelope);
    }
}
