package com.a501.service.domain.project.service;

import com.a501.service.domain.project.dto.MyProjectView;
import com.a501.service.domain.project.entity.Project;
import com.a501.service.domain.project.repository.ProjectRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 도메인이 프로젝트의 표시용 최소 정보만 가져가는 창구입니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectQueryService {
    private final ProjectRepository projectRepository;

    public List<MyProjectView> findMine(Integer userId) {
        return projectRepository.findByClientIdAndDeletedFalseOrderByIdDesc(userId).stream()
                .map(ProjectQueryService::toView)
                .toList();
    }

    private static MyProjectView toView(Project project) {
        return new MyProjectView(project.getId(), project.getName());
    }
}
