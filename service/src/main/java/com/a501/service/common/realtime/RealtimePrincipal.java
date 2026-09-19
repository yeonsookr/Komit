package com.a501.service.common.realtime;

import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import java.util.Objects;

public record RealtimePrincipal(
        Integer userId, String connectionId, RealtimeChannelType channel, String channelId) {

    public boolean owns(RealtimeChannelType otherChannel, String otherChannelId) {
        return channel == otherChannel && Objects.equals(channelId, otherChannelId);
    }
}
