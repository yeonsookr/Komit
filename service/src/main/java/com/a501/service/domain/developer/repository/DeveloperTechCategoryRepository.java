package com.a501.service.domain.developer.repository;

import com.a501.service.domain.developer.entity.DeveloperTechCategory;
import com.a501.service.domain.developer.entity.DeveloperTechCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeveloperTechCategoryRepository extends JpaRepository<DeveloperTechCategory, DeveloperTechCategoryId> {}
