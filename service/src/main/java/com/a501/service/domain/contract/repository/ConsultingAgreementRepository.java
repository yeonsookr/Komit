package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.ConsultingAgreement;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultingAgreementRepository extends JpaRepository<ConsultingAgreement, Integer> {
    List<ConsultingAgreement> findByContractIdInAndDeletedFalse(Collection<Integer> contractIds);

    /** 계약들 중 상태가 주어진 값인 컨설팅 합의. 마친 계약을 셀 때 {@code COMPLETED}로 부릅니다. */
    List<ConsultingAgreement> findByContractIdInAndDeletedFalseAndStatus(Collection<Integer> contractIds, String status);
}
