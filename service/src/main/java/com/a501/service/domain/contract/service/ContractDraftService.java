package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.contract.client.ContractDraftClient;
import com.a501.service.domain.contract.client.ContractDraftClient.GeneratedConsultingDraft;
import com.a501.service.domain.contract.dto.ContractDraftInput;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 전환된 컨설팅방의 무료 상담 대화로 계약서 version 1을 만들고 저장한다. */
@Service
@RequiredArgsConstructor
public class ContractDraftService {
    private static final String CONSULTING = "CONSULTING";

    private final ContractDraftPreparationService preparationService;
    private final ContractQueryService contractQueryService;
    private final ContractDraftClient draftClient;
    private final ContractDraftStore draftStore;
    private final ContractDetailService contractDetailService;

    public ContractResponse generate(Integer roomId, Integer userId) {
        ContractDraftInput input = preparationService.prepare(roomId, userId);
        if (!CONSULTING.equals(input.targetType())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        Integer existing = contractQueryService.findContractIdByRoomId(roomId).orElse(null);
        if (existing != null) {
            return contractDetailService.getContract(userId, existing);
        }

        GeneratedConsultingDraft generated = draftClient.generateConsulting(input);
        Integer contractId = draftStore.saveConsultingIfAbsent(roomId, generated);
        return contractDetailService.getContract(userId, contractId);
    }
}
