package com.a501.service.domain.contract.dto.request.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * R-QA-06: 인계 작업은 0원이고 일반 작업은 1원 이상이다. 위반은 {@code price} 필드 오류로 기록되어 전역 처리기가 목과
 * 같은 {@code milestones.N.price} / {@code 인계 작업은 0원이어야 합니다.} 또는 {@code 일반 작업은 1원 이상이어야
 * 합니다.}로 낸다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MilestonePriceValidator.class)
public @interface MilestonePrice {
    String message() default "{komit.workPrice}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
