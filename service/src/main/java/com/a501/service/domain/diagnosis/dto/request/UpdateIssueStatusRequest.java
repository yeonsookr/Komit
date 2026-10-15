package com.a501.service.domain.diagnosis.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "요구사항 처리 상태 변경 요청.")
public record UpdateIssueStatusRequest(
        @Schema(
                description = "바꿀 상태. RESOLVED 는 「직접 해결했어요」, UNRESOLVED 는 「되돌리기」다.",
                example = "RESOLVED")
        @NotNull Status status) {

    /** 의뢰인이 직접 오갈 수 있는 상태만 받는다. 제안·계약·만료는 사전점검 밖에서 정해진다. */
    public enum Status {
        UNRESOLVED,
        RESOLVED
    }
}
