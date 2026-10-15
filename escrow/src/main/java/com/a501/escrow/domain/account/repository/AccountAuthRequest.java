package com.a501.escrow.domain.account.repository;

import java.time.OffsetDateTime;

public record AccountAuthRequest(
        long id,
        long serviceClientId,
        String requestFingerprint,
        String userKeyLookupHash,
        String userKeyEncrypted,
        String accountNoEncrypted,
        String status,
        String transactionUniqueNo,
        OffsetDateTime expiresAt
) {
}
