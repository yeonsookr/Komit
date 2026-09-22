package com.a501.service.domain.project.repository;

import com.a501.service.domain.project.entity.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Integer> {
    List<Project> findByClientIdAndDeletedFalseOrderByIdDesc(Integer clientId);
}
