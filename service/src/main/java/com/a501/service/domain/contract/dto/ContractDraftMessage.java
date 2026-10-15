package com.a501.service.domain.contract.dto;

import java.time.Instant;

/** AI 계약 초안 요청에 넣을 상담 메시지 한 건. */
public record ContractDraftMessage(
        String chatRefId, AuthorRole authorRole, Instant createdAt, String content) {

    public enum AuthorRole {
        CLIENT,
        DEVELOPER
    }
}
