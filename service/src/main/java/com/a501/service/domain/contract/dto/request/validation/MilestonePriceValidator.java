package com.a501.service.domain.contract.dto.request.validation;

import com.a501.service.domain.contract.dto.request.WriteMilestoneRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link MilestonePrice} 구현. 금액이 없거나 음수면 {@code @NotNull}·{@code @PositiveOrZero} 가 따로 거르므로 여기서는
 * 0과 양수만 본다. 목도 같다.
 */
public class MilestonePriceValidator implements ConstraintValidator<MilestonePrice, WriteMilestoneRequest> {
    private static final String HANDOVER_PRICE = "{komit.handoverPrice}";

    @Override
    public boolean isValid(WriteMilestoneRequest value, ConstraintValidatorContext context) {
        if (value == null || value.price() == null || value.price() < 0) {
            return true;
        }
        boolean handover = Boolean.TRUE.equals(value.isHandover());
        if (handover == (value.price() == 0)) {
            return true;
        }
        String template = handover ? HANDOVER_PRICE : context.getDefaultConstraintMessageTemplate();
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(template)
                .addPropertyNode("price")
                .addConstraintViolation();
        return false;
    }
}
