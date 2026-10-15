package com.a501.service.common.realtime;

import com.a501.service.common.realtime.protocol.RealtimeChannelType;

public interface RealtimeChannelAuthorizer {
    RealtimeChannelType channel();

    void authorize(Integer userId, String channelId);
}
