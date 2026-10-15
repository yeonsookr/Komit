package com.a501.service.common.realtime.protocol;

public enum RealtimeSystemEvent {
    SYSTEM_PING,
    SYSTEM_PONG,
    SYSTEM_ERROR,
    /** 같은 사람이 더 새로운 연결을 열어 이 연결이 접힌다. 서버만 보낸다. */
    SYSTEM_REPLACED,
    /** 채널에 사람이 붙었다. 끊겼다 돌아온 경우에도 같은 쪽지가 간다. 서버만 보낸다. */
    SYSTEM_PEER_JOINED,
    /**
     * 채널에서 그 사람의 마지막 연결이 사라졌다. 서버만 보낸다.
     *
     * <p><b>나갔다는 뜻은 아니다.</b> 회선이 잠깐 끊겨도 온다. 받는 쪽은 바로 접지 말고
     * 돌아올 시간을 준다.
     */
    SYSTEM_PEER_LEFT,
    /**
     * 세션이 끝났다. 서버만 보낸다.
     *
     * <p>{@link #SYSTEM_PEER_LEFT} 와 다르다. 저쪽은 회선이 끊긴 것이고 이것은 일이 끝난
     * 것이다. 한 사람이 끝내면 양쪽이 받아 각자 녹음을 마감한다. (R-CON-15)
     */
    SYSTEM_SESSION_ENDED
}
