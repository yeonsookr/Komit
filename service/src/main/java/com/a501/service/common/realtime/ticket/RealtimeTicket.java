package com.a501.service.common.realtime.ticket;

import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;
import java.util.Objects;

public record RealtimeTicket(
        Integer userId,
        RealtimeChannelType channel,
        String channelId,
        RealtimeEventFamily allowedEventFamily) {

    public RealtimeTicket {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(allowedEventFamily, "allowedEventFamily");
        if (channel.allowedEventFamily() != allowedEventFamily) {
            throw new IllegalArgumentException(
                    "채널과 허용 이벤트 계열이 일치해야 한다: " + channel + ", " + allowedEventFamily);
        }
    }

    public RealtimeTicket(Integer userId, RealtimeChannelType channel, String channelId) {
        this(userId, channel, channelId, channel.allowedEventFamily());
    }
}
