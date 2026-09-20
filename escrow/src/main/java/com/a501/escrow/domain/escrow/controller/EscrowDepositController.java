package com.a501.escrow.domain.escrow.controller;

import com.a501.escrow.common.api.EscrowHeaderFactory;
import com.a501.escrow.common.api.EscrowSuccessResponse;
import com.a501.escrow.domain.escrow.dto.EscrowDtos;
import com.a501.escrow.domain.escrow.service.EscrowDepositService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/escrow")
public class EscrowDepositController {
    private final EscrowDepositService service;
    private final EscrowHeaderFactory headers;

    public EscrowDepositController(EscrowDepositService service, EscrowHeaderFactory headers) {
        this.service = service;
        this.headers = headers;
    }

    @PostMapping("/createEscrow")
    public EscrowSuccessResponse<EscrowDtos.AmountRecord> create(
            @Valid @RequestBody EscrowDtos.CreateRequest request) {
        return new EscrowSuccessResponse<>(headers.success(request.header()), service.create(request));
    }

    @PostMapping("/cancelEscrow")
    public EscrowSuccessResponse<EscrowDtos.CancelRecord> cancel(
            @Valid @RequestBody EscrowDtos.CancelRequest request) {
        return new EscrowSuccessResponse<>(headers.success(request.header()), service.cancel(request));
    }

    @PostMapping("/inquireEscrow")
    public EscrowSuccessResponse<EscrowDtos.InquireRecord> inquire(
            @Valid @RequestBody EscrowDtos.InquireRequest request) {
        return new EscrowSuccessResponse<>(headers.success(request.header()), service.inquire(request));
    }

    @PostMapping("/inquireEscrowList")
    public EscrowSuccessResponse<EscrowDtos.ListRecord> list(
            @Valid @RequestBody EscrowDtos.InquireListRequest request) {
        return new EscrowSuccessResponse<>(headers.success(request.header()), service.list(request));
    }
}
