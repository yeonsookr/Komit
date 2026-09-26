package com.a501.service.domain.contract.dto.request.validation;

import com.a501.service.domain.contract.dto.request.WriteMilestoneRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link NewWorkTitle} 구현. 빈 문자열은 {@code @Size} 가 따로 거르므로 여기서는 이름이 없는 것만 본다. 목도
 * 같다.
 */
public class NewWorkTitleValidator implements ConstraintValidator<NewWorkTitle, WriteMilestoneRequest> {
    @Override
    public boolean isValid(WriteMilestoneRequest value, ConstraintValidatorContext context) {
        if (value == null || value.issueId() != null || value.title() != null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("title")
                .addConstraintViolation();
        return false;
    }
}
