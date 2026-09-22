package com.a501.service.domain.diagnosis.dto.response;

import com.a501.service.domain.diagnosis.SurveyAnswerType;
import com.a501.service.domain.diagnosis.entity.SurveyQuestion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "설문 문항 하나.")
public record SurveyQuestionResponse(
        @Schema(description = "설문 문항 식별자.", example = "52") Integer questionId,
        @Schema(description = "사용자에게 보여줄 질문 문구.", example = "어떤 상태인가요?") String content,
        @Schema(description = "답을 받는 방식. TEXT는 주관식, SINGLE은 객관식 단수, MULTI는 객관식 복수다.", example = "MULTI")
        SurveyAnswerType answerType,
        @Schema(description = "제시된 보기. 주관식이면 답이 들어 있는 행 하나이고, 답 전이면 빈 배열이다.")
        List<SurveyOptionResponse> options) {
    public static SurveyQuestionResponse of(
            SurveyQuestion entity, List<SurveyOptionResponse> options) {
        return new SurveyQuestionResponse(
                entity.getId(), entity.getContent(), entity.answerType(), options);
    }
}
