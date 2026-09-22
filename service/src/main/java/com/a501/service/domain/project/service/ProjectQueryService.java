package com.a501.service.domain.project.service;

import com.a501.service.domain.project.entity.Project;
import com.a501.service.domain.project.repository.ProjectRepository;
import java.util.Optional;
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
}
