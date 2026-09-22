package com.a501.service.domain.developer.dto.request.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 경력의 종료일이 시작일보다 빠르지 않아야 합니다. 위반은 클래스가 아니라 {@code endDate} 필드 오류로 기록되어
 * 전역 처리기가 목과 같은 {@code careers.N.endDate} / {@code 시작일보다 빠를 수 없습니다.}로 냅니다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CareerPeriodValidator.class)
public @interface CareerPeriod {
    String message() default "{komit.careerPeriod}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
