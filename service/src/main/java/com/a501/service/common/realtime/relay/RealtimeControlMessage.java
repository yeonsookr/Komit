package com.a501.service.common.realtime.relay;

/** 어느 pod 에 붙어 있든 연결 하나를 지목해 다루기 위한 지시. */
public record RealtimeControlMessage(RealtimeControlAction action, String connectionId) {}
