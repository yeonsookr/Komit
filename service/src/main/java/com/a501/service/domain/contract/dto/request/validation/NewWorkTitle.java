package com.a501.service.domain.contract.dto.request.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 새 작업({@code issueId} 없음)에는 이름이 있어야 한다. 붙일 요구사항이 없어 이름을 여기서 받아야 한다. 위반은
 * 클래스가 아니라 {@code title} 필드 오류로 기록되어 전역 처리기가 목과 같은
 * {@code milestones.N.title} / {@code 필수 항목입니다.}로 낸다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NewWorkTitleValidator.class)
public @interface NewWorkTitle {
    String message() default "{jakarta.validation.constraints.NotNull.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
