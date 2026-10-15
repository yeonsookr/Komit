package com.a501.service.domain.contract.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 의뢰인의 확인 결과 저장. 다 체크하지 않아도 보낸다. 목 명세 {@code SaveQaResultsRequest}와 같다. */
@Schema(description = "확인 결과 저장. 다 체크하지 않아도 보낼 수 있다.")
public record SaveQaResultsRequest(
        @Schema(description = "이번에 체크한 확인 문장 식별자. 이미 통과한 것에 더하기만 한다. 체크는 되돌릴 수 "
                        + "없으므로 빼고 보내도 풀리지 않는다.", example = "[201]")
                @NotNull
                @Size(min = 1, message = "{komit.minItems}")
                List<@NotNull(message = "{komit.integer}") Integer> qaCriterionIds) {}
