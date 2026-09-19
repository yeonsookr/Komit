/**
 * 요청 추적과 계층별 호출 로깅. (BACKEND_CONVENTIONS 18)
 *
 * <ul>
 *   <li>{@link com.a501.service.common.logging.TraceIdFilter} — 요청마다 {@code traceId}를 만들어 MDC와 응답 헤더에 둡니다.</li>
 *   <li>{@link com.a501.service.common.logging.LoggingAspect} — 컨트롤러·서비스 호출의 시작·종료·소요 시간을 남깁니다.</li>
 *   <li>{@link com.a501.service.common.logging.LoggingProperties} — 느린 호출 기준 같은 로깅 설정값입니다.</li>
 * </ul>
 *
 * <p>로그 한 줄의 형식(앱 이름·인스턴스·버전·traceId)은 {@code src/main/resources/logback-spring.xml}이 정합니다.
 * 이 패키지는 값(토큰·계좌번호·요청 본문)을 절대 로그에 넣지 않습니다. 무엇이 얼마나 걸렸는지만 남깁니다.
 */
package com.a501.service.common.logging;
