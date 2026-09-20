package com.a501.service.common.realtime.relay;

import com.a501.service.common.realtime.protocol.RealtimeChannelType;

/** 중계에 쓰는 Redis pub/sub 채널 이름. 티켓이 쓰는 키 이름과는 다른 공간이다. */
public final class RealtimeRelayTopic {
    /**
     * 채널마다 구독을 붙였다 떼면 구독이 끝나기 전에 상대가 보낸 것이 사라진다. 세션 수가 적어
     * 전부 받아 놓고 이 pod 에 붙은 연결만 골라 보낸다.
     */
    public static final String PATTERN = "realtime:*";

    /**
     * 콜론이 아닌 빼기를 쓰는 것은 위 {@link #PATTERN} 에 걸리지 않게 하려는 것이다. 걸리면
     * 중계 subscriber 가 지시를 봉투로 읽으려다 매번 실패한다.
     */
    public static final String CONTROL = "realtime-control";

    private RealtimeRelayTopic() {}

    public static String of(RealtimeChannelType channel, String channelId) {
        return "realtime:" + channel.name() + ":" + channelId;
    }
}
