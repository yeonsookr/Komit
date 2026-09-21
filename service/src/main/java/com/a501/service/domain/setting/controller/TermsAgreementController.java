package com.a501.service.domain.setting.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.setting.dto.request.AgreeRequiredTermsRequest;
import com.a501.service.domain.setting.dto.response.RequiredTermsResponse;
import com.a501.service.domain.setting.service.TermsAgreementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Settings", description = "가입 필수 약관 동의 API")
@RestController
@RequestMapping("/api/settings/terms")
@RequiredArgsConstructor
public class TermsAgreementController {
    private final TermsAgreementService termsAgreementService;

    @Operation(summary = "필수 약관 동의 상태 조회")
    @GetMapping
    RequiredTermsResponse getRequiredTerms(@AuthenticationPrincipal AuthenticatedUser user) {
        return termsAgreementService.getRequiredTerms(user.userId());
    }

    @Operation(summary = "필수 약관 동의")
    @PutMapping
    RequiredTermsResponse agreeRequiredTerms(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AgreeRequiredTermsRequest request) {
        return termsAgreementService.agreeRequiredTerms(user.userId());
    }
}
