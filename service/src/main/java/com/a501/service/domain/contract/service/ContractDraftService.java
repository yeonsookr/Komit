package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.contract.client.ContractDraftClient;
import com.a501.service.domain.contract.client.ContractDraftClient.GeneratedConsultingDraft;
import com.a501.service.domain.contract.client.ContractDraftClient.GeneratedDevelopmentDraft;
import com.a501.service.domain.contract.dto.ContractDraftInput;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 전환된 방의 무료 상담 대화로 계약서 version 1을 만들고 저장한다. 개발방이면 요청서와 대화를 종합한다.
 *
 * <p>방에 계약서가 있으면 AI 를 다시 부르지 않고 그 계약서를 돌려준다. AI 초안이든 개발자가 고친 것이든 같다.
 */
@Service
@RequiredArgsConstructor
public class ContractDraftService {
    private static final String CONSULTING = "CONSULTING";
    private static final String DEVELOPMENT = "DEVELOPMENT";

    private final ContractDraftPreparationService preparationService;
    private final ContractQueryService contractQueryService;
    private final ContractDraftClient draftClient;
    private final ContractDraftStore draftStore;
    private final ContractDetailService contractDetailService;

    public ContractResponse generate(Integer roomId, Integer userId) {
        ContractDraftInput input = preparationService.prepare(roomId, userId);
        if (!CONSULTING.equals(input.targetType()) && !DEVELOPMENT.equals(input.targetType())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        Integer existing = contractQueryService.findContractIdByRoomId(roomId).orElse(null);
        if (existing != null) {
            return contractDetailService.getContract(userId, existing);
        }

        Integer contractId;
        if (CONSULTING.equals(input.targetType())) {
            GeneratedConsultingDraft generated = draftClient.generateConsulting(input);
            contractId = draftStore.saveConsultingIfAbsent(roomId, generated);
        } else {
            GeneratedDevelopmentDraft generated = draftClient.generateDevelopment(input);
            contractId = draftStore.saveDevelopmentIfAbsent(roomId, input.issues(), generated);
        }
        return contractDetailService.getContract(userId, contractId);
    }
}
