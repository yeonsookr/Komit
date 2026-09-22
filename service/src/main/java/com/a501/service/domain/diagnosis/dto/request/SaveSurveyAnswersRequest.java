package com.a501.service.domain.diagnosis.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "설문 한 단계의 답 저장 요청.")
public record SaveSurveyAnswersRequest(
        @Schema(description = "이 단계에서 답한 문항들. 한 문항만 바꿔도 그 문항만 보내면 된다.")
        @NotEmpty @Valid List<SurveyAnswerRequest> answers) {

    @Schema(description = "설문 문항 하나에 대한 답.")
    public record SurveyAnswerRequest(
            @Schema(description = "답할 설문 문항 식별자.", example = "52") @NotNull Integer questionId,
            @Schema(description = "고른 보기 식별자. 객관식 단수는 하나, 복수는 여럿, 주관식은 빈 배열이다.")
            List<Integer> selectedOptionIds,
            @Schema(description = "주관식 답. 객관식이면 null 이다.") @Size(max = 255) String text) {
        public List<Integer> selectedOptionIds() {
            return selectedOptionIds == null ? List.of() : selectedOptionIds;
        }
    }
}
