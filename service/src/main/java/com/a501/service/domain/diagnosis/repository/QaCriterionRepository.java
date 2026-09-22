package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.QaCriterion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaCriterionRepository extends JpaRepository<QaCriterion, Integer> {
    List<QaCriterion> findAllByIdInAndDeletedFalse(Collection<Integer> ids);

    /** 결함마다 따로 읽으면 결함 수만큼 쿼리가 늘어난다. 한 번에 읽어 결함에 나눠 붙인다. */
    List<QaCriterion> findByIssueIdInAndDeletedFalseOrderByIdAsc(Collection<Integer> issueIds);
}
