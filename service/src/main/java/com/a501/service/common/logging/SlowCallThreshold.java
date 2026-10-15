package com.a501.service.common.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 호출만 느린 호출 기준을 따로 둡니다. (BACKEND_CONVENTIONS 18.3)
 *
 * <p>{@code app.logging.slow-call-threshold} 는 모든 API 가 함께 쓰는 값입니다. 설계상 오래 걸리는
 * 호출이 있으면 그 하나 때문에 정상 성공마다 WARN 이 쌓이고, 진짜 이상 신호가 묻힙니다. 공용 값을
 * 올리면 반대로 다른 API 의 경고가 둔해집니다.
 *
 * <p>붙이지 않은 곳은 공용 값을 그대로 씁니다. 지금 이 값이 필요한 곳은 바깥 모델 왕복을 기다리는
 * 확인 문장 후보 생성뿐입니다.
 *
 * @see LoggingAspect
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface SlowCallThreshold {
    /** {@code 30s} 처럼 적습니다. 왜 그 값인지는 붙이는 자리에 주석으로 남깁니다. */
    String value();
}
