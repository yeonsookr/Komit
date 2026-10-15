package com.a501.service.common.realtime.protocol;

public enum RealtimeChannelType {
    CONSULTING_SESSION(RealtimeEventFamily.RTC),
    CHAT_ROOM(RealtimeEventFamily.CHAT);

    private final RealtimeEventFamily allowedEventFamily;

    RealtimeChannelType(RealtimeEventFamily allowedEventFamily) {
        this.allowedEventFamily = allowedEventFamily;
    }

    public RealtimeEventFamily allowedEventFamily() {
        return allowedEventFamily;
    }
}
