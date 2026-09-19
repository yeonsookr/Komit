package com.a501.service.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

@Configuration(proxyBeanMethods = false)
public class JacksonConfig {
    /**
     * 문자열 필드에 온 숫자·불리언을 거절합니다. 목(Pydantic)은 {@code "name": 123}을 "문자열이어야 합니다."로 거절하는데
     * Jackson 기본값은 "123"으로 바꿔 받아들여 두 서버의 응답이 달라집니다. 목이 거절하는 요청은 실서버도 거절해야 합니다. (BACKEND_CONVENTIONS 11)
     */
    @Bean
    JsonMapperBuilderCustomizer rejectScalarToStringCoercion() {
        return builder -> builder.withCoercionConfig(LogicalType.Textual, coercion -> coercion
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
