package com.a501.service.domain.project.service;

import com.a501.service.domain.project.entity.Project;
import com.a501.service.domain.project.repository.ProjectRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인이 프로젝트를 물어볼 때 쓰는 조회 창구.
 *
 * <p>엔티티와 리포지터리는 이 도메인 밖으로 나가지 않습니다. 묻는 쪽에는 id 만 돌려줍니다.
 * (BACKEND_CONVENTIONS 7.1)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectQueryService {
    private final ProjectRepository projectRepository;

    /**
     * 이 프로젝트를 맡긴 의뢰인.
     *
     * <p>없거나 지워진 프로젝트면 비어 있습니다. <b>여기서 예외를 던지지 않습니다</b> — 없을 때
     * 무엇으로 답할지는 묻는 쪽의 사정이라 그쪽이 정합니다.
     *
     * @param projectId 프로젝트 id
     * @return 의뢰인 사용자 id
     */
    public Optional<Integer> findClientId(Integer projectId) {
        return projectRepository.findByIdAndDeletedFalse(projectId).map(Project::getClientId);
    }

    /**
     * 화면에 보이는 프로젝트 이름.
     *
     * @param projectId 프로젝트 id
     */
    public Optional<String> findName(Integer projectId) {
        return projectRepository.findByIdAndDeletedFalse(projectId).map(Project::getName);
    }

    /**
     * 이 의뢰인이 맡긴 프로젝트.
     *
     * @param clientId 의뢰인 사용자 id
     * @return 프로젝트 id 목록
     */
    public List<Integer> findProjectIds(Integer clientId) {
        return projectRepository.findAllByClientIdAndDeletedFalse(clientId).stream()
                .map(Project::getId)
                .toList();
    }

    /**
     * 프로젝트 식별자별 의뢰인. 없거나 지워진 프로젝트는 결과에서 빠집니다.
     *
     * @param projectIds 프로젝트 id 목록
     * @return 프로젝트 id → 의뢰인 사용자 id
     */
    public Map<Integer, Integer> findClientIds(Collection<Integer> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return projectRepository.findAllByIdInAndDeletedFalse(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getClientId));
    }

    /**
     * 프로젝트 식별자별 이름. 없거나 지워진 프로젝트는 결과에서 빠집니다.
     *
     * @param projectIds 프로젝트 id 목록
     * @return 프로젝트 id → 이름
     */
    public Map<Integer, String> findNames(Collection<Integer> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return projectRepository.findAllByIdInAndDeletedFalse(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));
    }
}
