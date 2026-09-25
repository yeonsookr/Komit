package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.DevelopmentContract;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevelopmentContractRepository extends JpaRepository<DevelopmentContract, Integer> {
    Optional<DevelopmentContract> findByContractIdAndDeletedFalse(Integer contractId);

    /** 계약들 중 상태가 주어진 값인 개발 계약. 마친 계약을 셀 때 {@code COMPLETED}로 부릅니다. */
    List<DevelopmentContract> findByContractIdInAndDeletedFalseAndStatus(Collection<Integer> contractIds, String status);

    /** 계약들 중 상태가 주어진 값들 가운데 하나인 개발 계약. 마쳤거나 파기된 계약을 가릴 때 씁니다. */
    List<DevelopmentContract> findByContractIdInAndDeletedFalseAndStatusIn(
            Collection<Integer> contractIds, Collection<String> statuses);
}
