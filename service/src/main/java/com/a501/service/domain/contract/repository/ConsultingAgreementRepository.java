package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.ConsultingAgreement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultingAgreementRepository extends JpaRepository<ConsultingAgreement, Integer> {
    Optional<ConsultingAgreement> findByIdAndDeletedFalse(Integer id);

    Optional<ConsultingAgreement> findByContractIdAndDeletedFalse(Integer contractId);
}
