package com.a501.escrow.domain.escrow.controller;

import com.a501.escrow.common.api.EscrowHeaderFactory;
import com.a501.escrow.common.api.EscrowSuccessResponse;
import com.a501.escrow.domain.escrow.dto.EscrowHoldDtos;
import com.a501.escrow.domain.escrow.service.EscrowHoldService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/escrow")
public class EscrowHoldController {
    private final EscrowHoldService service;
    private final EscrowHeaderFactory headers;

    public EscrowHoldController(EscrowHoldService service, EscrowHeaderFactory headers) {
        this.service = service;
        this.headers = headers;
    }

    @PostMapping("/holdEscrow")
    public EscrowSuccessResponse<EscrowHoldDtos.Record> hold(
            @Valid @RequestBody EscrowHoldDtos.Request request) {
        return new EscrowSuccessResponse<>(headers.success(request.header()), service.hold(request));
    }

    @PostMapping("/unholdEscrow")
    public EscrowSuccessResponse<EscrowHoldDtos.Record> unhold(
            @Valid @RequestBody EscrowHoldDtos.Request request) {
        return new EscrowSuccessResponse<>(headers.success(request.header()), service.unhold(request));
    }
}
