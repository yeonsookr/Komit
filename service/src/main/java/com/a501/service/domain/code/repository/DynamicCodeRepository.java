package com.a501.service.domain.code.repository;

import com.a501.service.domain.code.entity.DynamicCode;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DynamicCodeRepository extends JpaRepository<DynamicCode, Integer> {
    List<DynamicCode> findByGroupIdAndActiveTrueOrderBySortOrderAscCodeIdAsc(Integer groupId);

    List<DynamicCode> findByCodeIdInAndGroupIdAndActiveTrue(Collection<Integer> codeIds, Integer groupId);
}
