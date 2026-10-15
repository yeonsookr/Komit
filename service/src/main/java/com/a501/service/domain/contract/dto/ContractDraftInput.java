package com.a501.service.domain.contract.dto;

import com.a501.service.domain.diagnosis.dto.DraftRequirements;
import java.util.List;

/**
 * 검증을 마치고 AI Worker에 전달할 계약 초안 입력.
 *
 * <p>개발 계약이면 요청서의 요구사항(`issues`)과 의뢰인이 고르지 않은 요구사항(`rejectedIssues`)을 함께
 * 보낸다. 컨설팅은 둘 다 비어 있다. (`docs/ai/chat-contract-draft.md`)
 */
public record ContractDraftInput(
        String targetType,
        List<ContractDraftMessage> messages,
        List<DraftRequirements.Requested> issues,
        List<DraftRequirements.LeftOut> rejectedIssues) {
    public ContractDraftInput {
        messages = List.copyOf(messages);
        issues = issues == null ? List.of() : List.copyOf(issues);
        rejectedIssues = rejectedIssues == null ? List.of() : List.copyOf(rejectedIssues);
    }

    /** 요청서 없이 채팅만 보내는 입력. 컨설팅 초안이 쓴다. */
    public ContractDraftInput(String targetType, List<ContractDraftMessage> messages) {
        this(targetType, messages, List.of(), List.of());
    }
}
