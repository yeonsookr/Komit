package com.a501.escrow.domain.member.controller;

import com.a501.escrow.common.api.EscrowHeaderFactory;
import com.a501.escrow.common.api.EscrowSuccessResponse;
import com.a501.escrow.domain.member.dto.MemberAccountDtos;
import com.a501.escrow.domain.member.service.MemberAccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/escrow")
public class MemberAccountController {
    private final MemberAccountService service;
    private final EscrowHeaderFactory headers;

    public MemberAccountController(MemberAccountService service, EscrowHeaderFactory headers) {
        this.service = service;
        this.headers = headers;
    }

    @PostMapping("/createMemberAccount")
    public EscrowSuccessResponse<MemberAccountDtos.CreateRecord> create(
            @Valid @RequestBody MemberAccountDtos.CreateRequest request) {
        return new EscrowSuccessResponse<>(headers.success(request.header()), service.create(request));
    }
}
