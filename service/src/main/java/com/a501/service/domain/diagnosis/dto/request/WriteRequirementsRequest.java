package com.a501.service.domain.diagnosis.dto.request;

import com.a501.service.domain.diagnosis.IssueSeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "요청서 저장 요청. 화면의 저장 버튼 하나가 이 요청 하나를 보낸다.")
public record WriteRequirementsRequest(
        @Schema(description = "요청서에 담을 요구사항 전부. 보낸 목록에 없는 기존 요구사항은 뺀 것으로 본다.")
        @NotNull @Valid List<WriteRequirement> requirements) {

    @Schema(description = "저장할 요구사항 하나.")
    public record WriteRequirement(
            @Schema(description = "이미 있는 요구사항이면 그 식별자, 이번에 새로 적는 것이면 null 이다.", example = "312")
            Integer issueId,
            @Schema(description = "요구사항 문장.", example = "관리자 API에 인증 추가")
            @NotBlank @Size(max = 200) String title,
            @Schema(description = "자세한 설명. 적지 않아도 된다.") @Size(max = 1000) String detail,
            @Schema(description = "결함 분류 식별자.", example = "71") @NotNull Integer categoryCodeId,
            @Schema(description = "위험도.", example = "HIGH") @NotNull IssueSeverity severity,
            @Schema(description = "이 요구사항의 확인 문장 전부. 보낸 목록에 없는 기존 문장은 지운 것으로 본다.")
            @NotNull @Valid List<WriteQaCriterion> qaCriteria) {}

    @Schema(description = "저장할 확인 문장 하나.")
    public record WriteQaCriterion(
            @Schema(description = "이미 있는 문장이면 그 식별자, 새로 적는 문장이면 null 이다.", example = "201")
            Integer qaCriterionId,
            @Schema(description = "무엇을 해보면 되는지.", example = "관리자 주소로 들어가면 로그인 화면이 뜬다")
            @NotBlank @Size(max = 500) String testCase) {}
}
