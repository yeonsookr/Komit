package com.a501.service.common.realtime.protocol;

import org.springframework.web.socket.CloseStatus;

/** 표준 코드로는 뜻이 갈리지 않는 종료 사유. */
public final class RealtimeCloseStatus {
    /**
     * 클라이언트는 이 코드에 <b>다시 붙지 않아야 한다</b>. 다시 붙으면 그 연결이 상대편을 접고
     * 상대편이 또 붙어, 두 탭이 끝없이 서로를 밀어낸다. 1001 이면 재접속 대상과 구별되지 않는다.
     *
     * <p>다만 코드만 믿어서는 안 된다. 보낼 것이 쌓인 연결에는 감싼 session 이 4500 을 쓰고,
     * 아예 닫히지 않기도 한다. 그래서 닫기 전에 {@code SYSTEM_REPLACED} 를 먼저 보낸다.
     */
    public static final CloseStatus DUPLICATE_CONNECTION =
            new CloseStatus(4001, "duplicate connection");

    private RealtimeCloseStatus() {}
}
