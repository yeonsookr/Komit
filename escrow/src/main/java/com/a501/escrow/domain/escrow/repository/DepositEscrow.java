package com.a501.escrow.domain.escrow.repository;

import java.math.BigInteger;

public record DepositEscrow(
        long escrowId,
        long transactionId,
        long serviceClientId,
        String contractId,
        String contractType,
        BigInteger amount,
        String escrowStatus,
        String transactionStatus,
        String idempotencyKey,
        String requestFingerprint,
        String depositStep,
        String depositorNameEncrypted,
        String virtualAccountId,
        String virtualAccountNo,
        String verificationStatus,
        BigInteger verifiedAmount,
        Boolean depositorNameMatched,
        String sourceBankCode,
        String sourceAccountEncrypted
) {
}
