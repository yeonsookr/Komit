package com.a501.service.domain.developer.repository;

import com.a501.service.domain.developer.entity.Career;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerRepository extends JpaRepository<Career, Integer> {
    /** 개발자의 살아 있는 경력. 시작일이 늦은 것, 곧 최근 경력이 먼저입니다. */
    List<Career> findByDeveloperIdAndDeletedFalseOrderByStartDateDescIdDesc(Integer developerId);
}
