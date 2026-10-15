package com.a501.service.domain.contract.dto.request.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * R-QA-06: 계약서에는 인계 작업이 정확히 하나 있다. 최종 인수 시험이 인계 작업의 확인이라 인계 작업이 없으면
 * 계약을 끝낼 수 없다. 여러 개는 아직 다루지 않는다. 위반은 {@code milestones} 필드 오류로 기록되어 전역
 * 처리기가 목과 같은 {@code milestones} / {@code 인계 작업은 하나여야 합니다.}로 낸다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SingleHandoverValidator.class)
public @interface SingleHandover {
    String message() default "{komit.handoverCount}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
