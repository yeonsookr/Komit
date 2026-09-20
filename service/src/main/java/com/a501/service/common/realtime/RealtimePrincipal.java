package com.a501.service.common.realtime;

import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;
import java.util.Objects;

public record RealtimePrincipal(
        Integer userId, String connectionId, RealtimeChannelType channel, String channelId) {

    public boolean owns(RealtimeChannelType otherChannel, String otherChannelId) {
        return channel == otherChannel && Objects.equals(channelId, otherChannelId);
    }

    /**
     * 티켓을 받은 채널이 어떤 이벤트를 다룰 수 있는지는 채널이 정한다. 세션 티켓으로 채팅
     * 이벤트를 부르는 교차 호출을 여기서 막는다. 연결 확인은 채널과 무관하므로 늘 통과시킨다.
     */
    public boolean allows(RealtimeEventFamily eventFamily) {
        return eventFamily == RealtimeEventFamily.SYSTEM
                || channel.allowedEventFamily() == eventFamily;
    }
}
