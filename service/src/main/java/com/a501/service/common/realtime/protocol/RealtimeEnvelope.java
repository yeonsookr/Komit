package com.a501.service.common.realtime.protocol;

import com.a501.service.common.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RealtimeEnvelope(
        Integer version,
        String type,
        String requestId,
        RealtimeChannelType channel,
        String channelId,
        JsonNode payload) {

    public static final int CURRENT_VERSION = 1;

    public static RealtimeEnvelope validated(RealtimeEnvelope envelope) {
        if (envelope == null
                || isBlank(envelope.type())
                || envelope.channel() == null
                || isBlank(envelope.channelId())) {
            throw new BusinessException(RealtimeErrorCode.MALFORMED_ENVELOPE);
        }
        if (envelope.version() == null || envelope.version() != CURRENT_VERSION) {
            throw new BusinessException(RealtimeErrorCode.UNSUPPORTED_PROTOCOL_VERSION);
        }
        if (envelope.family() == null) {
            throw new BusinessException(RealtimeErrorCode.UNKNOWN_EVENT_TYPE);
        }
        return envelope;
    }

    public RealtimeEventFamily family() {
        return RealtimeEventFamily.of(type);
    }

    public RealtimeEnvelope reply(String replyType, JsonNode replyPayload) {
        return new RealtimeEnvelope(
                CURRENT_VERSION, replyType, requestId, channel, channelId, replyPayload);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
