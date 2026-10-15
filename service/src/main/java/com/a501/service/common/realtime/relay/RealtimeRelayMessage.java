package com.a501.service.common.realtime.relay;

import com.a501.service.common.realtime.protocol.RealtimeEnvelope;

/**
 * {@code excludeConnectionId} 로 자기 echo 를 막으므로, 보낸 pod 도 남들과 똑같이 받아서
 * 뿌리면 된다.
 */
public record RealtimeRelayMessage(RealtimeEnvelope envelope, String excludeConnectionId) {}
