package com.a501.service.domain.project.dto;

import com.a501.service.domain.project.entity.Project;
import java.util.List;

/**
 * 다른 도메인에 건네는 프로젝트 한 건. 엔티티를 넘기지 않기 위한 경계다. (BACKEND_CONVENTIONS 7.1)
 */
public record ProjectSummary(
        Integer projectId,
        String name,
        String description,
        String domain,
        List<TechCategorySummary> techCategories) {
    public static ProjectSummary of(Project entity, List<TechCategorySummary> techCategories) {
        return new ProjectSummary(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getDomain(),
                List.copyOf(techCategories));
    }
}
