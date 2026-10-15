package com.a501.service.domain.code.repository;

import com.a501.service.domain.code.entity.DynamicCodeGroup;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DynamicCodeGroupRepository extends JpaRepository<DynamicCodeGroup, Integer> {
    Optional<DynamicCodeGroup> findByGroupKeyAndActiveTrue(String groupKey);
}
