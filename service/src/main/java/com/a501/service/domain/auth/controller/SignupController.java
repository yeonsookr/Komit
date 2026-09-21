package com.a501.service.domain.auth.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.auth.service.SignupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "가입 완료 API")
@RestController
@RequestMapping("/api/auth/signup/completion")
@RequiredArgsConstructor
public class SignupController {
    private final SignupService signupService;

    @Operation(summary = "가입 완료 상태 확인")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "가입 완료"),
            @ApiResponse(responseCode = "409", description = "SIGNUP_PENDING")})
    @GetMapping
    ResponseEntity<Void> getCompletion(@AuthenticationPrincipal AuthenticatedUser user) {
        signupService.requireComplete(user.userId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "가입 완료", description = "필수 약관과 회원 발급 계좌를 확인한 뒤 완료한다. (R-ACC-13)")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "가입 완료 또는 이미 완료"),
            @ApiResponse(responseCode = "409", description = "TERMS_NOT_AGREED"),
            @ApiResponse(responseCode = "503", description = "ESCROW_UNAVAILABLE")})
    @PostMapping
    ResponseEntity<Void> complete(@AuthenticationPrincipal AuthenticatedUser user) {
        signupService.complete(user.userId());
        return ResponseEntity.noContent().build();
    }
}
