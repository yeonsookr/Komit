package com.a501.service.common.realtime.presence;

/** 채널에 들어와 있는 연결 하나. 같은 사람이 여러 연결을 가질 수 있다. */
public record RealtimePresence(Integer userId, String connectionId) {}
