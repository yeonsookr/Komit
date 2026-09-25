package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.ConsultingAgreement;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultingAgreementRepository extends JpaRepository<ConsultingAgreement, Integer> {
    Optional<ConsultingAgreement> findByIdAndDeletedFalse(Integer id);

    Optional<ConsultingAgreement> findByContractIdAndDeletedFalse(Integer contractId);

    List<ConsultingAgreement> findByContractIdInAndDeletedFalse(Collection<Integer> contractIds);

    /** 계약들 중 상태가 주어진 값인 컨설팅 합의. 마친 계약을 셀 때 {@code COMPLETED}로 부릅니다. */
    List<ConsultingAgreement> findByContractIdInAndDeletedFalseAndStatus(Collection<Integer> contractIds, String status);

    /** 계약들 중 상태가 주어진 값들 가운데 하나인 컨설팅 합의. 마쳤거나 파기된 계약을 가릴 때 씁니다. */
    List<ConsultingAgreement> findByContractIdInAndDeletedFalseAndStatusIn(
            Collection<Integer> contractIds, Collection<String> statuses);
}
