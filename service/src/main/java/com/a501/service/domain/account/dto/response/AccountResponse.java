package com.a501.service.domain.account.dto.response;

public record AccountResponse(Integer accountId, String bankName,
        String maskedNumber, String accountHolder) {}
