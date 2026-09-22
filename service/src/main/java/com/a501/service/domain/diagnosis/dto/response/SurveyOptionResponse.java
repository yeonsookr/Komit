package com.a501.service.domain.diagnosis.dto.response;

import com.a501.service.domain.diagnosis.entity.SurveyOption;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "설문 보기 하나. 고르지 않은 보기도 그대로 내려간다.")
public record SurveyOptionResponse(
        @Schema(description = "설문 보기 식별자.", example = "104") Integer optionId,
        @Schema(description = "객관식이면 제시된 보기 문구, 주관식이면 사용자가 적은 답.", example = "보안이 걱정돼요")
        String content,
        @Schema(description = "이 보기를 골랐는지. 주관식은 답이 있으면 항상 참이다.", example = "true")
        boolean isSelected) {
    public static SurveyOptionResponse from(SurveyOption entity) {
        return new SurveyOptionResponse(entity.getId(), entity.getContent(), entity.isSelected());
    }
}
