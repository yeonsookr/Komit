package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.Milestone;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<Milestone, Integer> {
    List<Milestone> findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(
            Integer developmentContractId);
}
