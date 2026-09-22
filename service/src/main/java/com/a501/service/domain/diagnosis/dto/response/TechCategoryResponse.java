package com.a501.service.domain.diagnosis.dto.response;

import com.a501.service.domain.project.dto.TechCategorySummary;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로젝트가 쓰는 기술 한 줄.")
public record TechCategoryResponse(
        @Schema(description = "기술 항목 식별자.", example = "21") Integer techCodeId,
        @Schema(description = "화면에 그대로 보여주는 기술 이름.", example = "Next.js") String codeName) {
    public static TechCategoryResponse from(TechCategorySummary summary) {
        return new TechCategoryResponse(summary.techCodeId(), summary.codeName());
    }
}
