package com.a501.service.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** traceId가 요청 처리 동안만 MDC에 있고, 응답 헤더의 값과 같으며, 예외가 나도 지워지는지 확인합니다. */
class TraceIdFilterTest {
    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    @DisplayName("체인 실행 중에는 MDC와 응답 헤더에 같은 traceId가 있고, 끝나면 MDC에서 사라진다")
    void putsTraceIdDuringChainAndClearsAfterwards() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/default/1"), response,
                (request, res) -> seenInChain.set(MDC.get(MdcKeys.TRACE_ID)));

        assertThat(seenInChain.get()).matches("[0-9a-f]{32}");
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(seenInChain.get());
        assertThat(MDC.get(MdcKeys.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("다음 필터가 예외를 던져도 MDC를 비운다. 스레드가 풀로 돌아가 다른 요청의 로그에 섞이지 않게 한다")
    void clearsMdcWhenChainFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/default/1");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> { throw new ServletException("boom"); }))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get(MdcKeys.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("요청마다 다른 값을 만든다")
    void generatesDistinctIds() {
        assertThat(TraceIdFilter.newTraceId()).isNotEqualTo(TraceIdFilter.newTraceId());
    }
}
