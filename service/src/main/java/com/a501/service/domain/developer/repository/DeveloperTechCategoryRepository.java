package com.a501.service.domain.developer.repository;

import com.a501.service.domain.developer.entity.DeveloperTechCategory;
import com.a501.service.domain.developer.entity.DeveloperTechCategoryId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeveloperTechCategoryRepository extends JpaRepository<DeveloperTechCategory, DeveloperTechCategoryId> {
    /** 개발자의 기술 전부. 소프트 삭제된 행도 포함합니다. 같은 기술을 다시 넣을 때 되살리기 위해서입니다. */
    List<DeveloperTechCategory> findByIdDeveloperId(Integer developerId);
}
