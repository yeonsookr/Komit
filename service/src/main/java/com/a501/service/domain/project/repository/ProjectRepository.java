package com.a501.service.domain.project.repository;

import com.a501.service.domain.project.entity.Project;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Integer> {
    Optional<Project> findByIdAndDeletedFalse(Integer id);

    List<Project> findAllByClientIdAndDeletedFalse(Integer clientId);

    List<Project> findAllByIdInAndDeletedFalse(Collection<Integer> ids);
}
