package com.a501.service.common.realtime;

import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;
import java.util.Objects;

public record RealtimePrincipal(
        Integer userId,
        String connectionId,
        RealtimeChannelType channel,
        String channelId,
        RealtimeEventFamily allowedEventFamily) {

    public RealtimePrincipal {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(allowedEventFamily, "allowedEventFamily");
        if (channel.allowedEventFamily() != allowedEventFamily) {
            throw new IllegalArgumentException(
                    "채널과 허용 이벤트 계열이 일치해야 한다: " + channel + ", " + allowedEventFamily);
        }
    }

    public RealtimePrincipal(
            Integer userId, String connectionId, RealtimeChannelType channel, String channelId) {
        this(userId, connectionId, channel, channelId, channel.allowedEventFamily());
    }

    public boolean owns(RealtimeChannelType otherChannel, String otherChannelId) {
        return channel == otherChannel && Objects.equals(channelId, otherChannelId);
    }

    public boolean allows(RealtimeEventFamily eventFamily) {
        return eventFamily == RealtimeEventFamily.SYSTEM || allowedEventFamily == eventFamily;
    }
}
