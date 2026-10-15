package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.ContractSignature;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractSignatureRepository extends JpaRepository<ContractSignature, Integer> {
    Optional<ContractSignature> findByIdAndDeletedFalse(Integer id);
}
