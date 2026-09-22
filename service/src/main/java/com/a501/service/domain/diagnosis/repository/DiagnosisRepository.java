package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.Diagnosis;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Integer> {
    Optional<Diagnosis> findByIdAndDeletedFalse(Integer id);

    /** 재진단 여부를 가른다. 진단이 이미 있으면 회차를 올리고, 없으면 1회차로 연다. */
    Optional<Diagnosis> findByProjectIdAndDeletedFalse(Integer projectId);
}
