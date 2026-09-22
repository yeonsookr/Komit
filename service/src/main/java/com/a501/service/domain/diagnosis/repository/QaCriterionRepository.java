package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.QaCriterion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaCriterionRepository extends JpaRepository<QaCriterion, Integer> {
    List<QaCriterion> findAllByIdInAndDeletedFalse(Collection<Integer> ids);
}
