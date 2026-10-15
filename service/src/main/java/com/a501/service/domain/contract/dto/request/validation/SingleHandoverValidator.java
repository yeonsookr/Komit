package com.a501.service.domain.contract.dto.request.validation;

import com.a501.service.domain.contract.dto.request.WriteDevelopmentContractRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Objects;

/** {@link SingleHandover} 구현. 작업 목록이 비었으면 {@code @Size} 가 따로 거르므로 여기서는 보지 않는다. */
public class SingleHandoverValidator
        implements ConstraintValidator<SingleHandover, WriteDevelopmentContractRequest> {
    @Override
    public boolean isValid(WriteDevelopmentContractRequest value, ConstraintValidatorContext context) {
        if (value == null || value.milestones() == null || value.milestones().isEmpty()) {
            return true;
        }
        long handovers = value.milestones().stream()
                .filter(Objects::nonNull)
                .filter(milestone -> Boolean.TRUE.equals(milestone.isHandover()))
                .count();
        if (handovers == 1) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("milestones")
                .addConstraintViolation();
        return false;
    }
}
