package com.a501.service.domain.project.service;

import com.a501.service.domain.project.dto.ProjectSummary;
import com.a501.service.domain.project.entity.Project;
import com.a501.service.domain.project.repository.ProjectRepository;
import com.a501.service.domain.project.repository.ProjectTechCategoryRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectTechCategoryRepository projectTechCategoryRepository;

    /**
     * 의뢰인 본인의 프로젝트만 돌려준다. 없는 프로젝트와 남의 프로젝트를 갈라 답하지 않도록 둘 다 빈 값으로 내보내고, 어떤 오류로 답할지는 부르는 쪽이 정한다.
     */
    public Optional<ProjectSummary> findOwned(Integer projectId, Integer clientId) {
        return projectRepository
                .findByIdAndDeletedFalse(projectId)
                .filter(project -> project.getClientId().equals(clientId))
                .map(project ->
                        ProjectSummary.of(project, projectTechCategoryRepository.findTechCategories(project.getId())));
    }

    @Transactional
    public ProjectSummary create(Integer clientId, String name, String description) {
        Project saved = projectRepository.save(Project.of(clientId, name, description));
        return ProjectSummary.of(saved, List.of());
    }
}
