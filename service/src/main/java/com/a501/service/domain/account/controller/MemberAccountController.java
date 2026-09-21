package com.a501.service.domain.account.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.account.dto.response.MemberAccountResponse;
import com.a501.service.domain.account.service.MemberAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "회원 발급 계좌 연동")
@RestController
@RequestMapping("/api/accounts/member-account")
@RequiredArgsConstructor
public class MemberAccountController {
    private final MemberAccountService service;

    @Operation(summary = "내 발급 계좌 연동",
            description = "회원가입 때 발급한 계좌번호를 본인에게 돌려준다. 아직 없으면 발급한다. (R-ACC-14)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "발급된 계좌"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "503", description = "ESCROW_UNAVAILABLE")
    })
    @PostMapping
    MemberAccountResponse issue(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.issue(user.userId());
    }
}
