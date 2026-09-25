package com.a501.service.domain.contract.dto;

import java.util.List;

/** 검증을 마치고 AI Worker에 전달할 계약 초안 입력. */
public record ContractDraftInput(String targetType, List<ContractDraftMessage> messages) {
    public ContractDraftInput {
        messages = List.copyOf(messages);
    }
}
