package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.DevelopmentContract;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevelopmentContractRepository extends JpaRepository<DevelopmentContract, Integer> {}
