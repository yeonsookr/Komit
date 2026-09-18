package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.Inspection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionRepository extends JpaRepository<Inspection, Integer> {}
