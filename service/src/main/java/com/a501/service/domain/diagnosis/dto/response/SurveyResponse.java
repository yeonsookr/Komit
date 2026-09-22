package com.a501.service.domain.diagnosis.dto.response;

import com.a501.service.domain.diagnosis.entity.Survey;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "진단 한 회차에 딸린 설문지 한 벌.")
public record SurveyResponse(
        @Schema(description = "설문 식별자.", example = "19") Integer surveyId,
        @Schema(description = "이 설문을 받은 시점의 진단 회차.", example = "2") Integer diagnosisVersion,
        @Schema(description = "설문 문항. 화면에 보여줄 순서대로 내려간다.")
        List<SurveyQuestionResponse> questions,
        @Schema(description = "이 설문을 끝냈는지. 설문4까지 마치고 완료를 부르면 참이 된다.", example = "false")
        boolean isDone) {
    public static SurveyResponse of(Survey entity, List<SurveyQuestionResponse> questions) {
        return new SurveyResponse(
                entity.getId(),
                entity.getDiagnosisVersion().intValue(),
                questions,
                entity.isDone());
    }
}
