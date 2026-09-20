package com.a501.service.common.realtime.protocol;

public enum RealtimeSystemEvent {
    SYSTEM_PING,
    SYSTEM_PONG,
    SYSTEM_ERROR,
    /** 같은 사람이 더 새로운 연결을 열어 이 연결이 접힌다. 서버만 보낸다. */
    SYSTEM_REPLACED
}
