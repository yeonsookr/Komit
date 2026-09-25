package com.a501.service.domain.mypage.controller;

import static com.a501.service.common.security.SecuritySliceTestSupport.expiredToken;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.file.FileErrorCode;
import com.a501.service.domain.mypage.dto.response.MyAccountResponse;
import com.a501.service.domain.mypage.service.MyAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 목 명세 {@code PUT /api/users/me/profile-image}와 같은 계약인지 봅니다. 기대 본문은 목에 같은 요청을 보내 받은 것입니다. */
@WebMvcSecurityTest(MyAccountController.class)
class MyAccountControllerTest {
    private static final String PATH = "/api/users/me/profile-image";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private MyAccountService myAccountService;

    @Test
    @DisplayName("올린 사진을 걸면 토큰의 주인 계정에 걸고 계정 칸을 목 계약 그대로 돌려준다")
    void changesProfileImageOfTokenOwner() throws Exception {
        when(myAccountService.changeProfileImage(12, 512)).thenReturn(new MyAccountResponse(
                "김미미", "mimi@kakao.com", "https://cdn.komit.example.com/profiles/512.png"));

        mockMvc.perform(authenticated("{\"fileId\":512}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"name":"김미미","email":"mimi@kakao.com","profileImageUrl":"https://cdn.komit.example.com/profiles/512.png"}
                        """, true));
    }

    @Test
    @DisplayName("걸 수 없는 파일이면 400 FILE_NOT_ATTACHABLE이다")
    void unattachableFileIsBadRequest() throws Exception {
        when(myAccountService.changeProfileImage(12, 400))
                .thenThrow(new BusinessException(FileErrorCode.FILE_NOT_ATTACHABLE));

        mockMvc.perform(authenticated("{\"fileId\":400}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"FILE_NOT_ATTACHABLE","message":"쓸 수 없는 파일입니다."}
                        """, true));
    }

    @Test
    @DisplayName("fileId가 없으면 422이고 서비스는 불리지 않는다")
    void missingFileIdIsInvalidRequest() throws Exception {
        mockMvc.perform(authenticated("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"fileId","reason":"필수 항목입니다."}]}
                        """, true));

        verify(myAccountService, never()).changeProfileImage(any(), any());
    }

    @Test
    @DisplayName("fileId가 정수가 아니면 422이고 서비스는 불리지 않는다")
    void nonIntegerFileIdIsInvalidRequest() throws Exception {
        mockMvc.perform(authenticated("{\"fileId\":\"abc\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"fileId","reason":"정수여야 합니다."}]}
                        """, true));

        verify(myAccountService, never()).changeProfileImage(any(), any());
    }

    @Test
    @DisplayName("토큰이 없거나 만료되면 401이고 서비스는 불리지 않는다")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"fileId\":512}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}
                        """, true));
        mockMvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"fileId\":512}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken(12)))
                .andExpect(status().isUnauthorized());

        verify(myAccountService, never()).changeProfileImage(any(), any());
    }

    private MockHttpServletRequestBuilder authenticated(String body) {
        return put(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
