package com.a501.service.common.logging;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 {@code traceId}를 만들어 MDC와 응답 헤더에 싣는 서블릿 필터. (BACKEND_CONVENTIONS 18)
 *
 * <p>가장 먼저 실행되도록 최상위 순서를 주어, 보안 필터가 401·403을 쓰는 경우와 전역 예외 처리기가 500을 쓰는 경우에도
 * 같은 요청의 모든 로그와 응답 헤더에 같은 값이 남습니다. 문의가 들어오면 이 값으로 서버 로그를 찾습니다.
 *
 * <p>값은 서버가 매번 새로 만드는 UUID입니다. 클라이언트가 보낸 헤더를 그대로 받아 쓰지 않는 이유는 로그에 임의 문자열이
 * 섞여 들어오는 것(로그 인젝션)과 다른 요청을 사칭하는 것을 막기 위함입니다. 응답 본문 계약({@code {code, message}})에는
 * 넣지 않고 헤더로만 제공합니다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    /** 응답에 traceId를 싣는 헤더 이름. */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * MDC에 traceId를 넣고 다음 필터를 실행한 뒤, 스레드가 풀로 돌아가기 전에 반드시 지웁니다.
     *
     * @param request     현재 요청
     * @param response    현재 응답. 처리 전에 {@value #TRACE_ID_HEADER} 헤더를 씁니다.
     * @param filterChain 다음 필터
     * @throws ServletException 다음 필터가 던진 예외
     * @throws IOException      다음 필터가 던진 예외
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = newTraceId();
        MDC.put(MdcKeys.TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.TRACE_ID);
        }
    }

    /**
     * 새 traceId를 만듭니다. 뜻이 없는 임의 값이어야 하므로 UUID를 씁니다.
     *
     * @return 하이픈을 뺀 32자리 16진수 문자열
     */
    static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
