package com.a501.service.domain.project.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.code.entity.DynamicCode;
import com.a501.service.domain.code.service.CodeQueryService;
import com.a501.service.domain.project.dto.ProjectSummary;
import com.a501.service.domain.project.entity.Project;
import com.a501.service.domain.project.entity.ProjectTechCategory;
import com.a501.service.domain.project.repository.ProjectRepository;
import com.a501.service.domain.project.repository.ProjectTechCategoryRepository;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {
    private static final String TECH_CATEGORY_GROUP = "TECH_CATEGORY";

    private final ProjectRepository projectRepository;
    private final ProjectTechCategoryRepository projectTechCategoryRepository;
    private final CodeQueryService codeQueryService;
    private final EntityManager entityManager;

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

    /** 의뢰인이 가진 프로젝트. 이름만 쓰는 목록 화면을 위해 기술 목록은 비워 둔다. */
    public List<ProjectSummary> findAllOwned(Integer clientId) {
        return projectRepository.findByClientIdAndDeletedFalseOrderByIdDesc(clientId).stream()
                .map(project -> ProjectSummary.of(project, List.of()))
                .toList();
    }

    /**
     * 검사에서 확인한 기술로 갈아 끼운다. 코드북에 없는 이름은 담을 자리가 없어 건너뛴다. 검사 도구는 기술 이름을 자유 문자열로 보내므로 시드에 없는 이름이 언제든 올 수 있다.
     */
    @Transactional
    public void replaceTechCategories(Integer projectId, List<String> codeNames) {
        if (codeNames == null || codeNames.isEmpty()) {
            return;
        }
        Collection<Integer> techCodeIds =
                codeQueryService.findCodeIdsByName(TECH_CATEGORY_GROUP, codeNames).values();
        if (techCodeIds.isEmpty()) {
            return;
        }
        Project project = projectRepository
                .findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR));

        projectTechCategoryRepository.deleteByIdProjectId(projectId);
        techCodeIds.stream()
                // 코드북은 다른 도메인이라 리포지토리를 직접 부르지 않는다. 식별자만 아는 상태로
                // 연관을 걸면 되므로 프록시 참조를 쓴다. (BACKEND_CONVENTIONS 7.1)
                .map(codeId -> ProjectTechCategory.of(
                        project, entityManager.getReference(DynamicCode.class, codeId)))
                .forEach(projectTechCategoryRepository::save);
    }

    @Transactional
    public ProjectSummary create(Integer clientId, String name, String description) {
        Project saved = projectRepository.save(Project.of(clientId, name, description));
        return ProjectSummary.of(saved, List.of());
    }
}
