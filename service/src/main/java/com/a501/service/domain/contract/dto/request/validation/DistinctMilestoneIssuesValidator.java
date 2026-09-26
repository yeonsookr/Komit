package com.a501.service.domain.contract.dto.request.validation;

import com.a501.service.domain.contract.dto.request.WriteDevelopmentContractRequest;
import com.a501.service.domain.contract.dto.request.WriteMilestoneRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** {@link DistinctMilestoneIssues} 구현. 새 작업은 아직 요구사항이 없어 겹칠 것이 없다. */
public class DistinctMilestoneIssuesValidator
        implements ConstraintValidator<DistinctMilestoneIssues, WriteDevelopmentContractRequest> {
    @Override
    public boolean isValid(WriteDevelopmentContractRequest value, ConstraintValidatorContext context) {
        if (value == null || value.milestones() == null) {
            return true;
        }
        Set<Integer> seen = new HashSet<>();
        boolean distinct = value.milestones().stream()
                .filter(Objects::nonNull)
                .map(WriteMilestoneRequest::issueId)
                .filter(Objects::nonNull)
                .allMatch(seen::add);
        if (distinct) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("milestones")
                .addConstraintViolation();
        return false;
    }
}
