package com.a501.service.domain.developer.dto.request.validation;

import com.a501.service.domain.developer.dto.request.WriteCareerRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** {@link CareerPeriod} 구현. 날짜 형식이 어긋난 값은 {@code @Pattern}이 따로 거르므로 여기서는 통과시킵니다. */
public class CareerPeriodValidator implements ConstraintValidator<CareerPeriod, WriteCareerRequest> {

    @Override
    public boolean isValid(WriteCareerRequest value, ConstraintValidatorContext context) {
        if (value == null || value.startDate() == null || value.endDate() == null) {
            return true;
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(value.startDate());
            end = LocalDate.parse(value.endDate());
        } catch (DateTimeParseException invalidFormat) {
            return true;
        }
        if (!end.isBefore(start)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("endDate")
                .addConstraintViolation();
        return false;
    }
}
