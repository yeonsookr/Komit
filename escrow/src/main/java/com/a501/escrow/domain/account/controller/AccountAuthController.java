package com.a501.escrow.domain.account.controller;

import com.a501.escrow.common.api.EscrowHeaderFactory;
import com.a501.escrow.common.api.EscrowSuccessResponse;
import com.a501.escrow.domain.account.dto.AccountAuthDtos;
import com.a501.escrow.domain.account.service.AccountAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/edu")
public class AccountAuthController {
    private final AccountAuthService service;
    private final EscrowHeaderFactory headers;

    public AccountAuthController(AccountAuthService service, EscrowHeaderFactory headers) {
        this.service = service;
        this.headers = headers;
    }

    @PostMapping("/accountAuth/openAccountAuth")
    public EscrowSuccessResponse<AccountAuthDtos.OpenRecord> open(
            @Valid @RequestBody AccountAuthDtos.OpenRequest request) {
        return new EscrowSuccessResponse<>(headers.success(request.header()), service.open(request));
    }

    @PostMapping("/accountAuth/checkAuthCode")
    public EscrowSuccessResponse<AccountAuthDtos.CheckRecord> check(
            @Valid @RequestBody AccountAuthDtos.CheckRequest request) {
        return new EscrowSuccessResponse<>(headers.success(request.header()), service.check(request));
    }

    @PostMapping("/demandDeposit/inquireTransactionHistoryList")
    public EscrowSuccessResponse<AccountAuthDtos.HistoryRecord> history(
            @Valid @RequestBody AccountAuthDtos.HistoryRequest request) {
        return new EscrowSuccessResponse<>(headers.success(request.header()), service.history(request));
    }
}
