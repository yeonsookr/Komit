package com.a501.service.common.logging;

/**
 * MDC(Mapped Diagnostic Context)에 넣는 키 이름 모음.
 *
 * <p>{@code logback-spring.xml}의 패턴({@code %X{traceId}})과 글자가 같아야 하므로 문자열을 흩어 두지 않고 여기서만 정의합니다.
 */
public final class MdcKeys {
    /** 요청 하나를 가리키는 임의 식별자. {@link TraceIdFilter}가 넣고 지웁니다. */
    public static final String TRACE_ID = "traceId";

    private MdcKeys() {
    }
}
