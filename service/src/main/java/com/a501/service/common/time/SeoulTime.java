package com.a501.service.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * DB의 UTC {@link LocalDateTime}을 API 경계의 한국 시각 {@link OffsetDateTime}으로 바꿉니다.
 * 목 명세의 일시 예시({@code 2026-08-14T18:20:00+09:00})와 같은 표기입니다. (BACKEND_CONVENTIONS 11.1)
 */
public final class SeoulTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private SeoulTime() {
    }

    public static OffsetDateTime of(LocalDateTime utc) {
        return utc == null ? null : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONE).toOffsetDateTime();
    }
}
