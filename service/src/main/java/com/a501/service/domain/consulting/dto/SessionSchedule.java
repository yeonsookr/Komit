package com.a501.service.domain.consulting.dto;

import java.time.LocalDateTime;

/**
 * 컨설팅 세션 한 건의 예약과 실제 진행. 시각은 모두 UTC 입니다.
 *
 * <p>정산 도메인이 세션 엔티티 없이 「언제 끝나기로 했고 실제로 열렸나」를 보도록 넘깁니다.
 * (BACKEND_CONVENTIONS 7.1)
 *
 * @param sessionId 세션 id
 * @param scheduledAt 예약한 시작 시각. 예약이 없으면 {@code null}
 * @param durationMinutes 예약한 길이(분)
 * @param startedAt 양쪽이 모여 처음 들어간 시각. 열리지 않았으면 {@code null} (`R-CON-13`)
 * @param endedAt 종료가 확정된 시각. 끝나지 않았으면 {@code null} (`R-CON-15`)
 */
public record SessionSchedule(
        Integer sessionId,
        LocalDateTime scheduledAt,
        int durationMinutes,
        LocalDateTime startedAt,
        LocalDateTime endedAt) {

    /** 예약상 끝나는 시각. 이의 제기 기간은 여기서부터 셉니다 (`R-CON-10`). 예약이 없으면 {@code null} */
    public LocalDateTime scheduledEnd() {
        return scheduledAt == null ? null : scheduledAt.plusMinutes(durationMinutes);
    }

    /** 세션이 열렸다가 종료까지 확정됐는지. 아니면 정상 진행을 확인할 수 없는 세션입니다 (`R-CON-12`). */
    public boolean ranToEnd() {
        return startedAt != null && endedAt != null;
    }
}
