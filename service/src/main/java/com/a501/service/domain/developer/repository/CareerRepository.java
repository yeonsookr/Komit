package com.a501.service.domain.developer.repository;

import com.a501.service.domain.developer.entity.Career;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareerRepository extends JpaRepository<Career, Integer> {
    /** 개발자의 살아 있는 경력. 시작일이 늦은 것, 곧 최근 경력이 먼저입니다. */
    List<Career> findByDeveloperIdAndDeletedFalseOrderByStartDateDescIdDesc(Integer developerId);

    boolean existsByDeveloperIdAndDeletedFalseAndVerifiedTrue(Integer developerId);

    /** 증빙이 확인된 경력을 하나라도 가진 개발자 식별자. 목록 카드의 '증빙 확인' 표시를 한 번의 조회로 채우기 위한 것입니다. */
    @Query("""
            select distinct career.developerId
            from Career career
            where career.developerId in :developerIds and career.deleted = false and career.verified = true
            """)
    List<Integer> findVerifiedDeveloperIds(@Param("developerIds") Collection<Integer> developerIds);
}
