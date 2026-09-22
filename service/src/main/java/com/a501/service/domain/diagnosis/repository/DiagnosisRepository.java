package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.Diagnosis;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Integer> {
    Optional<Diagnosis> findByProjectIdAndDeletedFalse(Integer projectId);
}
