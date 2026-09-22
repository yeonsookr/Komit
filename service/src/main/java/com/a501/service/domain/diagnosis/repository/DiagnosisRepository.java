package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.Diagnosis;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Integer> {
    List<Diagnosis> findByProjectIdInAndDeletedFalse(Collection<Integer> projectIds);
}
