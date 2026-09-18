package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<Milestone, Integer> {}
