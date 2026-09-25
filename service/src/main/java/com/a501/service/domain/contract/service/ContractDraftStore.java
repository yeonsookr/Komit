package com.a501.service.domain.contract.service;

import com.a501.service.domain.contract.client.ContractDraftClient.GeneratedConsultingDraft;
import com.a501.service.domain.contract.entity.AiContractDraftSnapshot;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.repository.AiContractDraftSnapshotRepository;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AI 호출이 끝난 뒤 계약서 version 1과 원본만 짧은 트랜잭션으로 저장한다. */
@Service
@RequiredArgsConstructor
public class ContractDraftStore {
    private final ContractRepository contractRepository;
    private final ConsultingAgreementRepository consultingAgreementRepository;
    private final AiContractDraftSnapshotRepository snapshotRepository;

    @Transactional
    public Integer saveConsultingIfAbsent(Integer roomId, GeneratedConsultingDraft draft) {
        return contractRepository.findByRoomIdAndDeletedFalse(roomId)
                .map(Contract::getId)
                .orElseGet(() -> saveConsulting(roomId, draft));
    }

    private Integer saveConsulting(Integer roomId, GeneratedConsultingDraft draft) {
        Contract contract =
                contractRepository.save(Contract.of(roomId, Contract.ContractType.CONSULTING));
        consultingAgreementRepository.save(
                ConsultingAgreement.of(contract.getId(), draft.topic(), 0, draft.detail()));
        snapshotRepository.save(
                AiContractDraftSnapshot.of(contract.getId(), draft.contentSnapshot()));
        return contract.getId();
    }
}
