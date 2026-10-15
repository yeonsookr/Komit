package com.a501.service.domain.auth.controller;

import static com.a501.service.common.security.SecuritySliceTestSupport.expiredToken;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.auth.AuthErrorCode;
import com.a501.service.domain.auth.dto.response.KakaoLoginResponse;
import com.a501.service.domain.auth.dto.response.LoginUserResponse;
import com.a501.service.domain.auth.dto.response.TokenResponse;
import com.a501.service.domain.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 목 Swagger(/service/docs)의 인증 계약과 보안 체인을 함께 검증합니다.
 * 상태 코드, 응답 코드, JSON 구조가 목과 같아야 합니다. (BACKEND_CONVENTIONS 19)
 */
@WebMvcSecurityTest(AuthController.class)
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("카카오 로그인은 토큰 없이 부를 수 있고 계약 필드를 그대로 돌려준다")
    void kakaoLoginIsPublicAndReturnsContractBody() throws Exception {
        when(authService.loginWithKakao("code", "https://front/callback")).thenReturn(new KakaoLoginResponse(
                "access", "refresh", 900, true, new LoginUserResponse(12, "김미미", null, false)));

        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"code\",\"redirectUri\":\"https://front/callback\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.user.userId").value(12))
                .andExpect(jsonPath("$.user.name").value("김미미"))
                .andExpect(jsonPath("$.user.profileImageFileId").value((Object) null))
                .andExpect(jsonPath("$.user.hasDevProfile").value(false));
    }

    @Test
    @DisplayName("카카오 로그인 실패는 401 KAKAO_AUTH_FAILED로 나가고 계정 존재 여부를 드러내지 않는다")
    void kakaoLoginFailureUsesContractErrorCode() throws Exception {
        when(authService.loginWithKakao(any(), any()))
                .thenThrow(new BusinessException(AuthErrorCode.KAKAO_AUTH_FAILED));

        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"invalid-code\",\"redirectUri\":\"https://front/callback\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("KAKAO_AUTH_FAILED"))
                .andExpect(jsonPath("$.message").value("로그인하지 못했습니다. 다시 시도해 주세요."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("카카오 로그인은 code와 redirectUri가 둘 다 필수다")
    void kakaoLoginRequiresBothContractFields() throws Exception {
        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.errors[0].reason").value("필수 항목입니다."))
                .andExpect(jsonPath("$.errors[1].field").value("redirectUri"))
                .andExpect(jsonPath("$.errors[1].reason").value("필수 항목입니다."));
    }

    @Test
    @DisplayName("재발급은 토큰 없이 부를 수 있고 회전된 토큰을 돌려준다")
    void refreshIsPublicAndRotatesToken() throws Exception {
        when(authService.refresh("current")).thenReturn(new TokenResponse("access", "next", 900));

        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"current\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("next"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("쓸 수 없는 리프레시 토큰은 401 REFRESH_TOKEN_INVALID다")
    void refreshWithInvalidTokenIsUnauthorized() throws Exception {
        when(authService.refresh("expired-refresh-token"))
                .thenThrow(new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID));

        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"expired-refresh-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.message").value("로그인이 만료되었습니다. 다시 로그인해 주세요."));
    }

    @Test
    @DisplayName("재발급은 refreshToken이 필수다")
    void refreshRequiresRefreshToken() throws Exception {
        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("refreshToken"))
                .andExpect(jsonPath("$.errors[0].reason").value("필수 항목입니다."));
    }

    @Test
    @DisplayName("로그아웃은 인증 사용자의 세션을 지우고 204를 준다")
    void logoutDeletesSessionOfAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(authService).logout(12);
    }

    @Test
    @DisplayName("토큰 없이 로그아웃하면 401 UNAUTHORIZED JSON이다")
    void logoutWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

        verify(authService, never()).logout(any());
    }

    @Test
    @DisplayName("만료된 액세스 토큰으로 로그아웃하면 401 UNAUTHORIZED다")
    void logoutWithExpiredTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken(12)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(authService, never()).logout(any());
    }
}
