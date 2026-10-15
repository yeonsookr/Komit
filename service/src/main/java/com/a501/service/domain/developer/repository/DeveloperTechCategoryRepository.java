package com.a501.service.domain.developer.repository;

import com.a501.service.domain.developer.entity.DeveloperTechCategory;
import com.a501.service.domain.developer.entity.DeveloperTechCategoryId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeveloperTechCategoryRepository extends JpaRepository<DeveloperTechCategory, DeveloperTechCategoryId> {
    /** 개발자의 기술 전부. 소프트 삭제된 행도 포함합니다. 같은 기술을 다시 넣을 때 되살리기 위해서입니다. */
    List<DeveloperTechCategory> findByIdDeveloperId(Integer developerId);

    /** 여러 개발자의 살아 있는 기술. 목록 카드의 칩을 한 번의 조회로 채우기 위한 것입니다. */
    List<DeveloperTechCategory> findByIdDeveloperIdInAndDeletedFalse(Collection<Integer> developerIds);
}
