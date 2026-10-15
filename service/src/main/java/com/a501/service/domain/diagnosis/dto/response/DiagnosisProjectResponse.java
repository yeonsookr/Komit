package com.a501.service.domain.diagnosis.dto.response;

import com.a501.service.domain.project.dto.ProjectSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "진단이 걸린 프로젝트.")
public record DiagnosisProjectResponse(
        @Schema(description = "프로젝트 식별자.", example = "8") Integer projectId,
        @Schema(description = "프로젝트 이름.", example = "수제 비누 가게") String name,
        @Schema(description = "한 줄 설명. 적지 않았으면 null 이다.", example = "수제 비누를 파는 작은 온라인 가게")
        String description,
        @Schema(description = "연결된 도메인 주소. 없으면 null 이다.") String domain,
        @Schema(description = "프로젝트에 쓰인 기술. 검사에서 확인되지 않으면 빈 배열이다.")
        List<TechCategoryResponse> techCategories) {
    public static DiagnosisProjectResponse from(ProjectSummary summary) {
        return new DiagnosisProjectResponse(
                summary.projectId(),
                summary.name(),
                summary.description(),
                summary.domain(),
                summary.techCategories().stream().map(TechCategoryResponse::from).toList());
    }
}
