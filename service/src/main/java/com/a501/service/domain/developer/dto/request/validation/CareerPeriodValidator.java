package com.a501.service.domain.developer.dto.request.validation;

import com.a501.service.domain.developer.dto.request.WriteCareerRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** {@link CareerPeriod} 구현. 무효한 달력 날짜와 시작일보다 빠른 종료일을 필드 오류로 기록합니다. */
public class CareerPeriodValidator implements ConstraintValidator<CareerPeriod, WriteCareerRequest> {
    private static final String INVALID_FORMAT = "{jakarta.validation.constraints.Pattern.message}";

    @Override
    public boolean isValid(WriteCareerRequest value, ConstraintValidatorContext context) {
        if (value == null || value.startDate() == null) {
            return true;
        }
        if (!value.startDate().matches(WriteCareerRequest.DATE_PATTERN)
                || value.endDate() != null && !value.endDate().matches(WriteCareerRequest.DATE_PATTERN)) {
            return true;
        }
        LocalDate start = parse(value.startDate(), "startDate", context);
        LocalDate end = value.endDate() == null ? null : parse(value.endDate(), "endDate", context);
        if (start == null || value.endDate() != null && end == null) {
            return false;
        }
        if (end == null) {
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

    private static LocalDate parse(String raw, String field, ConstraintValidatorContext context) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException invalidFormat) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(INVALID_FORMAT)
                    .addPropertyNode(field)
                    .addConstraintViolation();
            return null;
        }
    }
}
