package com.a501.service.domain.contract.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 의뢰인의 작업 통과. 목 명세 {@code ConfirmMilestoneRequest}와 같다. 빈 목록은 형식 오류가 아니라 다 체크하지 않은
 * 것이라 서비스가 400 으로 답한다.
 */
@Schema(description = "작업 통과 처리.")
public record ConfirmMilestoneRequest(
        @Schema(description = "체크한 확인 문장 식별자. 전부 체크해야 통과시킬 수 있다.", example = "[201, 202]")
                @NotNull
                List<@NotNull Integer> qaCriterionIds) {}
