package com.a501.service.domain.consulting.realtime;

import java.time.LocalDateTime;

/**
 * 세션이 끝났다는 사실. 종료가 DB 에 기록된 뒤에 전해집니다.
 *
 * @param sessionId 끝난 세션
 * @param endedAt 최초 종료 시각. 같은 요청이 다시 와도 이 값은 바뀌지 않는다 (R-CON-15)
 */
public record ConsultingSessionEnded(Integer sessionId, LocalDateTime endedAt) {}
