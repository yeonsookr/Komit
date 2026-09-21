package com.a501.service.domain.account.dto.response;

import java.time.OffsetDateTime;

public record AccountVerificationResponse(
        Integer verificationId,
        String bankName,
        String maskedNumber,
        String accountHolder,
        OffsetDateTime expiresAt) {}
