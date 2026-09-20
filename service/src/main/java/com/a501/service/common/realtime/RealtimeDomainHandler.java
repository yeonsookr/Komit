package com.a501.service.common.realtime;

import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;

public interface RealtimeDomainHandler {
    RealtimeEventFamily family();

    boolean supports(String type);

    void handle(RealtimePrincipal principal, RealtimeEnvelope envelope);
}
