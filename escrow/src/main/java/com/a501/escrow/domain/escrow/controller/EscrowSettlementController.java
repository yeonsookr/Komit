package com.a501.escrow.domain.escrow.controller;

import com.a501.escrow.common.api.EscrowHeaderFactory;
import com.a501.escrow.common.api.EscrowSuccessResponse;
import com.a501.escrow.domain.escrow.dto.EscrowSettlementDtos;
import com.a501.escrow.domain.escrow.service.EscrowSettlementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/escrow")
public class EscrowSettlementController {
    private final EscrowSettlementService service;
    private final EscrowHeaderFactory headers;

    public EscrowSettlementController(EscrowSettlementService service, EscrowHeaderFactory headers) {
        this.service = service;
        this.headers = headers;
    }

    @PostMapping("/releaseEscrow")
    public EscrowSuccessResponse<EscrowSettlementDtos.ReleaseRecord> release(
            @Valid @RequestBody EscrowSettlementDtos.ReleaseRequest request) {
        return new EscrowSuccessResponse<>(headers.success(request.header()), service.release(request));
    }

    @PostMapping("/refundEscrow")
    public EscrowSuccessResponse<EscrowSettlementDtos.RefundRecord> refund(
            @Valid @RequestBody EscrowSettlementDtos.RefundRequest request) {
        return new EscrowSuccessResponse<>(headers.success(request.header()), service.refund(request));
    }
}
