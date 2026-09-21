package com.a501.service.domain.consulting.realtime;

import java.util.Arrays;

/**
 * 통화를 트기까지 두 사람이 주고받는 쪽지의 종류입니다.
 *
 * <p>내용은 서버가 읽지 않습니다. 어느 종류인지만 보고 상대에게 그대로 넘깁니다.
 */
public enum ConsultingSignalEvent {
    /** 「나 준비됐다」. 상대가 이걸 받고 먼저 제안을 만듭니다. */
    RTC_PEER_READY,
    /** 「내 화면과 소리는 이런 형식이다」 — 제안. */
    RTC_OFFER,
    /** 그 제안에 대한 답. */
    RTC_ANSWER,
    /** 「나한테 오는 길은 이런 게 있다」 — 후보 하나. 여러 번 옵니다. */
    RTC_ICE_CANDIDATE,
    /** 길이 끊겨 처음부터 다시 찾자는 요청. */
    RTC_ICE_RESTART,
    /** 「나 나간다」. 상대 화면을 정리하게 합니다. */
    RTC_PEER_LEFT;

    public static boolean isKnown(String type) {
        return Arrays.stream(values()).anyMatch(event -> event.name().equals(type));
    }
}
