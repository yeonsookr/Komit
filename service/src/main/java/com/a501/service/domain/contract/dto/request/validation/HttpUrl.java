package com.a501.service.domain.contract.dto.request.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * R-QA-06: 인수 시험 URL은 호스트가 있는 {@code http}·{@code https} 주소만 받는다. 의뢰인이 누르는 주소라
 * {@code javascript:} 같은 다른 스킴을 막는다. 문구는 목과 같은 {@code http 또는 https 주소여야 합니다.}다.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HttpUrlValidator.class)
public @interface HttpUrl {
    String message() default "{komit.materialUrl}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
