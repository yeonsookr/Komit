package com.a501.service.domain.diagnosis.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

@Schema(description = "진단 시작 요청. projectId 를 주면 그 프로젝트를 다시 진단하고, 주지 않으면 프로젝트를 새로 만든다.")
public record StartDiagnosisRequest(
        @Schema(description = "다시 진단할 프로젝트 식별자. 새로 만들 때는 비운다.")
        Integer projectId,
        @Schema(description = "새로 만들 프로젝트 이름. projectId 를 주지 않았다면 반드시 채운다.", example = "수제 비누 가게")
        @Size(max = 100) String name,
        @Schema(description = "프로젝트 한 줄 설명. 적지 않아도 된다.", example = "수제 비누를 파는 작은 온라인 가게")
        @Size(max = 255) String description) {
    /**
     * 새 프로젝트는 이름이 있어야 한다. 위반 사유를 {@code name} 필드에 달기 위해 그 이름의 getter 로 선언한다. 메서드 이름을 바꾸면 응답의 필드 이름도 함께 바뀐다.
     */
    @AssertTrue(message = "필수 항목입니다.")
    @Schema(hidden = true)
    public boolean isName() {
        return projectId != null || (name != null && !name.isBlank());
    }
}
