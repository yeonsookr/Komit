package com.a501.escrow.common.api;

public record EscrowRequestHeader(
        String apiName,
        String transmissionDate,
        String transmissionTime,
        String institutionCode,
        String fintechAppNo,
        String apiServiceCode,
        String institutionTransactionUniqueNo,
        String clientApiKey,
        String idempotencyKey,
        String userKey
) {
}
