package com.a501.service.common.realtime.ticket;

import com.a501.service.common.realtime.protocol.RealtimeChannelType;

public record RealtimeTicket(Integer userId, RealtimeChannelType channel, String channelId) {
}
