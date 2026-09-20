package com.a501.escrow.domain.reconciliation.controller;

import com.a501.escrow.common.api.EscrowHeaderFactory;
import com.a501.escrow.common.api.EscrowSuccessResponse;
import com.a501.escrow.domain.reconciliation.dto.ReconciliationDtos;
import com.a501.escrow.domain.reconciliation.service.ReconciliationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/escrow")
public class ReconciliationController {
    private final ReconciliationService service;
    private final EscrowHeaderFactory headers;

    public ReconciliationController(ReconciliationService service, EscrowHeaderFactory headers) {
        this.service = service;
        this.headers = headers;
    }

    @PostMapping("/inquireReconciliation")
    public EscrowSuccessResponse<ReconciliationDtos.Record> inquire(
            @Valid @RequestBody ReconciliationDtos.Request request) {
        return new EscrowSuccessResponse<>(headers.success(request.header()), service.inquire(request));
    }
}
