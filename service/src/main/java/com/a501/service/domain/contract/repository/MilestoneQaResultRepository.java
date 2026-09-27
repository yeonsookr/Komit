package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.MilestoneQaResult;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneQaResultRepository extends JpaRepository<MilestoneQaResult, Integer> {
    List<MilestoneQaResult> findAllByMilestoneIdInAndDeletedFalseOrderByIdAsc(
            Collection<Integer> milestoneIds);

    List<MilestoneQaResult> findAllByMilestoneIdAndDeletedFalseOrderByIdAsc(Integer milestoneId);
}
