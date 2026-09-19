package com.a501.escrow.domain.member.repository;

import java.time.OffsetDateTime;

public record MemberAccountProvision(
        long id, long serviceClientId, String userId, String accountTypeUniqueNo,
        String idempotencyKey, String requestFingerprint, String provisionStep,
        String provisionStatus, OffsetDateTime stepRequestedAt, String userKeyEncrypted,
        String bankCode, String accountNoEncrypted, long seedAmount,
        String seedStatus, String seedRequestReference, String seedTransactionNo
) {
}
