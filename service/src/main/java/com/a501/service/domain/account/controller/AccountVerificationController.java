package com.a501.service.domain.account.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.account.dto.request.ConfirmAccountVerificationRequest;
import com.a501.service.domain.account.dto.request.RequestAccountVerificationRequest;
import com.a501.service.domain.account.dto.response.AccountResponse;
import com.a501.service.domain.account.dto.response.AccountVerificationResponse;
import com.a501.service.domain.account.service.AccountVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "거래 대금 수령 계좌 1원 인증")
@RestController
@RequestMapping("/api/accounts/verifications")
@RequiredArgsConstructor
public class AccountVerificationController {
    private final AccountVerificationService service;

    @Operation(summary = "계좌 1원 보내기", description = "가입 및 마이페이지의 동일한 계좌 인증 흐름이다. (R-ACC-02)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "1원 요청과 확인 건 생성"),
        @ApiResponse(responseCode = "400", description = "ACCOUNT_REJECTED 또는 VERIFICATION_TOO_MANY"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "422", description = "INVALID_REQUEST"),
        @ApiResponse(responseCode = "503", description = "ESCROW_UNAVAILABLE")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountVerificationResponse request(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RequestAccountVerificationRequest request) {
        return service.request(user.userId(), request);
    }

    @Operation(summary = "계좌 확인 · 등록", description = "인증된 계좌를 현재 수령 계좌로 등록한다. (R-ACC-04)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "계좌 등록 또는 교체"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "VERIFICATION_NOT_FOUND"),
        @ApiResponse(responseCode = "422", description = "INVALID_REQUEST")
    })
    @PostMapping("/{verificationId}/confirm")
    AccountResponse confirm(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer verificationId,
            @Valid @RequestBody ConfirmAccountVerificationRequest request) {
        return service.confirm(user.userId(), verificationId, request);
    }
}
