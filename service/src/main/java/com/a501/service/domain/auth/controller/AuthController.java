package com.a501.service.domain.auth.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.auth.dto.request.KakaoLoginRequest;
import com.a501.service.domain.auth.dto.request.RefreshTokenRequest;
import com.a501.service.domain.auth.dto.response.KakaoLoginResponse;
import com.a501.service.domain.auth.dto.response.TokenResponse;
import com.a501.service.domain.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "카카오 로그인, 토큰 재발급, 로그아웃 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @Operation(summary = "카카오 로그인", description = "카카오 인가 코드로 로그인한다. 신규 계정은 이 요청으로 생성되며 가입 화면에서 필수 약관에 별도로 동의한다. (R-ACC-12)")
    @PostMapping("/kakao")
    KakaoLoginResponse loginWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
        return authService.loginWithKakao(request.code(), request.redirectUri());
    }

    @Operation(summary = "액세스 토큰 재발급", description = "리프레시 토큰으로 새 액세스 토큰을 받는다. 리프레시 토큰도 함께 새 값으로 바뀐다. (R-AUTH-03)")
    @PostMapping("/token/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(summary = "로그아웃", description = "로그인 상태를 끝낸다. 이후 이 리프레시 토큰으로는 토큰을 다시 받을 수 없다.")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser user) {
        authService.logout(user.userId());
        return ResponseEntity.noContent().build();
    }
}
