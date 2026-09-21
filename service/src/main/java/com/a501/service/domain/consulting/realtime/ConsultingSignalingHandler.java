package com.a501.service.domain.consulting.realtime;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.RealtimeDomainHandler;
import com.a501.service.common.realtime.RealtimePrincipal;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeErrorCode;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;
import com.a501.service.common.realtime.relay.RealtimeRelayPublisher;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * 통화 쪽지를 같은 세션의 상대에게 넘깁니다.
 *
 * <p>협상 식별자와 이벤트별 필수 필드만 확인하고 실제 SDP와 ICE 내용은 해석하지 않습니다.
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
        validatePayload(envelope);
        relayPublisher.publish(envelope, principal.connectionId());
    }

    private void validatePayload(RealtimeEnvelope envelope) {
        JsonNode payload = envelope.payload();
        if (payload == null || !payload.isObject() || !hasNonBlankText(payload, "negotiationId")) {
            malformedEnvelope();
        }

        ConsultingSignalEvent event;
        try {
            event = ConsultingSignalEvent.valueOf(envelope.type());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(RealtimeErrorCode.UNKNOWN_EVENT_TYPE);
        }

        switch (event) {
            case RTC_OFFER, RTC_ANSWER -> requireObjectPayload(payload, "description", 2);
            case RTC_ICE_CANDIDATE -> requireObjectPayload(payload, "candidate", 2);
            case RTC_PEER_READY, RTC_ICE_RESTART, RTC_PEER_LEFT -> requireFieldCount(payload, 1);
        }
    }

    private void requireObjectPayload(JsonNode payload, String field, int fieldCount) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isObject()) {
            malformedEnvelope();
        }
        requireFieldCount(payload, fieldCount);
    }

    private void requireFieldCount(JsonNode payload, int fieldCount) {
        if (payload.size() != fieldCount) {
            malformedEnvelope();
        }
    }

    private boolean hasNonBlankText(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value != null && value.isString() && !value.asString().isBlank();
    }

    private void malformedEnvelope() {
        throw new BusinessException(RealtimeErrorCode.MALFORMED_ENVELOPE);
    }
}
