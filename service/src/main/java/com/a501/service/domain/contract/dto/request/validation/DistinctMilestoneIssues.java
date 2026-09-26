package com.a501.service.domain.contract.dto.request.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 한 요구사항에는 작업이 하나다. 같은 요구사항을 두 작업에 담으면 어느 작업의 통과가 그 요구사항의 통과인지
 * 가를 수 없다. 위반은 {@code milestones} 필드 오류로 기록되어 전역 처리기가 목과 같은
 * {@code milestones} / {@code 같은 요구사항을 두 작업에 담을 수 없습니다.}로 낸다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DistinctMilestoneIssuesValidator.class)
public @interface DistinctMilestoneIssues {
    String message() default "{komit.duplicateIssue}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
