package com.a501.service.domain.project.repository;

import com.a501.service.domain.project.entity.ProjectTechCategory;
import com.a501.service.domain.project.entity.ProjectTechCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTechCategoryRepository extends JpaRepository<ProjectTechCategory, ProjectTechCategoryId> {}
