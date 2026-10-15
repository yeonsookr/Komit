package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.AiContractDraftSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiContractDraftSnapshotRepository
        extends JpaRepository<AiContractDraftSnapshot, Integer> {
    Optional<AiContractDraftSnapshot> findByContractId(Integer contractId);
}
