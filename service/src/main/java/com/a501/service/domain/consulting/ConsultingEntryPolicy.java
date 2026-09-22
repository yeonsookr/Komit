package com.a501.service.domain.consulting;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 세션에 들어갈 수 있는 때를 판정합니다.
 *
 * <p>입장 API 와 실시간 티켓 발급이 같은 값을 봐야 합니다. 한쪽만 열어 두면 화면은 들여보내는데
 * 소켓이 막히거나 그 반대가 됩니다.
 */
public final class ConsultingEntryPolicy {
    /** 세션 시작 전 몇 분부터 들어갈 수 있는지. (R-CON-08) */
    private static final Duration ENTRY_OPENS_BEFORE = Duration.ofMinutes(10);

    private ConsultingEntryPolicy() {}

    /**
     * 예약 시각을 기준으로 지금 들어갈 수 있는지.
     *
     * @param scheduledAt 예약한 일시
     * @param now 지금
     */
    public static boolean isOpen(LocalDateTime scheduledAt, LocalDateTime now) {
        return !now.isBefore(scheduledAt.minus(ENTRY_OPENS_BEFORE));
    }
}
