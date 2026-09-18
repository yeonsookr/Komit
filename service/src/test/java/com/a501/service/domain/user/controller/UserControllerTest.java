package com.a501.service.domain.user.controller;

import static com.a501.service.common.security.SecuritySliceTestSupport.expiredToken;
import static com.a501.service.common.security.SecuritySliceTestSupport.tokenSignedWithOtherKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.user.dto.response.MeResponse;
import com.a501.service.domain.user.service.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * docs/jwt-samples.md 「검증 케이스」 1·2·3·6번과 내 정보 조회 계약을 검증합니다.
 */
@WebMvcSecurityTest(UserController.class)
class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private UserQueryService userQueryService;

    @Test
    @DisplayName("유효한 토큰이면 토큰의 사용자로 내 정보를 조회한다")
    void validTokenReturnsMe() throws Exception {
        when(userQueryService.getMe(12)).thenReturn(new MeResponse(12, "김미미", null, false, null, 3));

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(12))
                .andExpect(jsonPath("$.name").value("김미미"))
                .andExpect(jsonPath("$.profileImageFileId").value((Object) null))
                .andExpect(jsonPath("$.hasDevProfile").value(false))
                .andExpect(jsonPath("$.developerId").value((Object) null))
                .andExpect(jsonPath("$.unreadNotificationCount").value(3));
    }

    @Test
    @DisplayName("토큰이 없으면 401 UNAUTHORIZED JSON이다")
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

        verify(userQueryService, never()).getMe(any());
    }

    @Test
    @DisplayName("만료된 토큰이면 401 UNAUTHORIZED다")
    void expiredTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken(12)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(userQueryService, never()).getMe(any());
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰(운영자 토큰)은 사용자 영역에서 401이다")
    void tokenSignedWithOtherKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSignedWithOtherKey(1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(userQueryService, never()).getMe(any());
    }

    @Test
    @DisplayName("Bearer 형식이 아닌 Authorization 헤더는 401이다")
    void nonBearerAuthorizationIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
