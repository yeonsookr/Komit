package com.a501.service.common.logging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 로깅 설정값. {@code application.yml}의 {@code app.logging.*}에 대응합니다.
 *
 * @param slowCallThreshold 이 시간을 넘긴 컨트롤러·서비스 호출을 {@link LoggingAspect}가 WARN으로 남기는 기준.
 *                          {@link SlowCallThreshold}가 붙은 호출은 그 값이 이 값을 대신합니다.
 *                          설정이 없으면 1초입니다. 0이나 음수는 기동 시 거절합니다.
 */
@ConfigurationProperties(prefix = "app.logging")
public record LoggingProperties(@DefaultValue("1s") Duration slowCallThreshold) {

    /**
     * 바인딩된 값이 뜻이 있는 범위인지 확인합니다.
     *
     * @throws IllegalArgumentException 느린 호출 기준이 0 이하일 때
     */
    public LoggingProperties {
        if (slowCallThreshold == null || slowCallThreshold.isZero() || slowCallThreshold.isNegative()) {
            throw new IllegalArgumentException("app.logging.slow-call-threshold must be positive");
        }
    }
}
