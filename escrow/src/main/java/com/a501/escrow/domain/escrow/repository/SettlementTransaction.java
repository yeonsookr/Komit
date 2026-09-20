package com.a501.escrow.domain.escrow.repository;

import java.math.BigInteger;

public record SettlementTransaction(
        long id,
        long settlementId,
        long escrowId,
        String type,
        BigInteger amount,
        String status,
        String referenceId,
        String idempotencyKey,
        String requestFingerprint,
        String recipientBankCode,
        String recipientAccountEncrypted,
        String recipientName,
        String financialTransactionId
) {
}
