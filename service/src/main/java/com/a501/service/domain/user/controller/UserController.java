package com.a501.service.domain.user.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.user.dto.response.MeResponse;
import com.a501.service.domain.user.service.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "계정과 공통 사용자 정보 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserQueryService userQueryService;

    @Operation(summary = "내 정보 조회", description = "모든 화면의 헤더를 채우는 내 정보다. 아바타와 알림 배지를 한 번에 받는다.")
    @GetMapping("/me")
    MeResponse getMe(@AuthenticationPrincipal AuthenticatedUser user) {
        return userQueryService.getMe(user.userId());
    }
}
