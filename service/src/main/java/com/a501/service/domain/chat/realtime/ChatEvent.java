package com.a501.service.domain.chat.realtime;

import java.util.Arrays;

/** 대화방에서 오가는 쪽지의 종류. */
public enum ChatEvent {
    /** 「이 말을 보낸다」. 보낸 사람이 올리는 유일한 종류다. */
    CHAT_MESSAGE;

    public static boolean isKnown(String type) {
        return Arrays.stream(values()).anyMatch(event -> event.name().equals(type));
    }
}
