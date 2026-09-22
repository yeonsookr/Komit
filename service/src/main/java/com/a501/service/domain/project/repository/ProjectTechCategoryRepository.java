package com.a501.service.domain.project.repository;

import com.a501.service.domain.project.dto.TechCategorySummary;
import com.a501.service.domain.project.entity.ProjectTechCategory;
import com.a501.service.domain.project.entity.ProjectTechCategoryId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectTechCategoryRepository
        extends JpaRepository<ProjectTechCategory, ProjectTechCategoryId> {
    /** 화면에 그대로 붙는 기술 이름까지 한 번에 읽는다. 코드를 따로 조회하면 기술 수만큼 쿼리가 늘어난다. */
    @Query("""
            select new com.a501.service.domain.project.dto.TechCategorySummary(
                    c.techCode.codeId, c.techCode.codeName)
            from ProjectTechCategory c
            where c.id.projectId = :projectId and c.deleted = false
            order by c.techCode.sortOrder asc, c.techCode.codeId asc
            """)
    List<TechCategorySummary> findTechCategories(@Param("projectId") Integer projectId);

    /** 검사할 때마다 확인한 기술이 달라질 수 있어 통째로 갈아 끼운다. 이력을 남기는 표가 아니다. */
    void deleteByIdProjectId(Integer projectId);
}
