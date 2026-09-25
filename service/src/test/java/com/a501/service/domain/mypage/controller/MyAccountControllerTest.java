package com.a501.service.domain.mypage.controller;

import static com.a501.service.common.security.SecuritySliceTestSupport.expiredToken;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.config.JacksonConfig;
import com.a501.service.domain.file.FileErrorCode;
import com.a501.service.domain.mypage.MyPageErrorCode;
import com.a501.service.domain.mypage.dto.response.MyAccountResponse;
import com.a501.service.domain.mypage.dto.response.WithdrawalBlockerResponse;
import com.a501.service.domain.mypage.dto.response.WithdrawalBlockerResponse.Type;
import com.a501.service.domain.mypage.dto.response.WithdrawalCheckResponse;
import com.a501.service.domain.mypage.dto.response.WithdrawalResultResponse;
import com.a501.service.domain.mypage.service.MyAccountService;
import com.a501.service.domain.mypage.service.WithdrawalService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 목 명세 {@code 8 계정 · 공통}의 사진 바꾸기·이름 바꾸기·탈퇴 전 확인·탈퇴와 같은 계약인지 봅니다.
 * 기대 본문은 목에 같은 요청을 보내 받은 것이거나 목 명세의 예시입니다.
 */
@WebMvcSecurityTest(MyAccountController.class)
@Import(JacksonConfig.class) // 문자열 칸에 온 숫자를 거절하는 실서버 설정. 슬라이스는 이 설정을 싣지 않습니다.
class MyAccountControllerTest {
    private static final String PATH = "/api/users/me/profile-image";
    private static final String ME = "/api/users/me";
    private static final String WITHDRAWAL = "/api/users/me/withdrawal";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private MyAccountService myAccountService;
    @MockitoBean
    private WithdrawalService withdrawalService;

    @Test
    @DisplayName("이름을 바꾸면 토큰의 주인 이름을 바꾸고 계정 칸을 목 계약 그대로 돌려준다")
    void changesNameOfTokenOwner() throws Exception {
        when(myAccountService.changeName(12, "김미미")).thenReturn(new MyAccountResponse(
                "김미미", "mimi@kakao.com", "https://k.kakaocdn.net/dn/profile/mimi.jpg"));

        mockMvc.perform(withToken(patch(ME)).content("{\"name\":\"김미미\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"name":"김미미","email":"mimi@kakao.com","profileImageUrl":"https://k.kakaocdn.net/dn/profile/mimi.jpg"}
                        """, true));
    }

    @Test
    @DisplayName("앞뒤 공백은 다듬지 않고 그대로 넘긴다. 목이 그대로 받기 때문이다")
    void nameIsNotTrimmed() throws Exception {
        when(myAccountService.changeName(12, " 김미미 ")).thenReturn(new MyAccountResponse(" 김미미 ", null, null));

        mockMvc.perform(withToken(patch(ME)).content("{\"name\":\" 김미미 \"}"))
                .andExpect(status().isOk());

        verify(myAccountService).changeName(12, " 김미미 ");
    }

    @Test
    @DisplayName("name이 없거나 비었거나 20자를 넘거나 문자열이 아니면 목과 같은 422이고 서비스는 불리지 않는다")
    void invalidNameIsInvalidRequest() throws Exception {
        expectNameError("{}", "필수 항목입니다.");
        expectNameError("{\"name\":\"\"}", "1자 이상이어야 합니다.");
        expectNameError("{\"name\":\"" + "가".repeat(21) + "\"}", "20자 이하여야 합니다.");
        expectNameError("{\"name\":123}", "문자열이어야 합니다.");

        verify(myAccountService, never()).changeName(any(), any());
    }

    @Test
    @DisplayName("이름 20자는 받는다")
    void twentyCharacterNameIsAccepted() throws Exception {
        String name = "가".repeat(20);
        when(myAccountService.changeName(12, name)).thenReturn(new MyAccountResponse(name, null, null));

        mockMvc.perform(withToken(patch(ME)).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("탈퇴 전 확인은 막는 것을 목 예시 그대로 늘어놓는다")
    void checkWithdrawalMatchesMockExample() throws Exception {
        when(withdrawalService.check(12)).thenReturn(WithdrawalCheckResponse.of(List.of(
                new WithdrawalBlockerResponse(Type.CONTRACT, "수제 비누 가게 · 진행 중인 의뢰", null, 130, 93),
                new WithdrawalBlockerResponse(Type.DEPOSIT, "예치금 650,000원", 650_000L, 130, 93))));

        mockMvc.perform(withToken(get(WITHDRAWAL)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"canWithdraw":false,"blockers":[
                          {"type":"CONTRACT","title":"수제 비누 가게 · 진행 중인 의뢰","amount":null,"targetId":130,"roomId":93},
                          {"type":"DEPOSIT","title":"예치금 650,000원","amount":650000,"targetId":130,"roomId":93}]}
                        """, true));
    }

    @Test
    @DisplayName("막는 것이 없으면 canWithdraw가 참이고 blockers는 빈 배열이다")
    void checkWithdrawalWithoutBlockers() throws Exception {
        when(withdrawalService.check(12)).thenReturn(WithdrawalCheckResponse.of(List.of()));

        mockMvc.perform(withToken(get(WITHDRAWAL)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"canWithdraw\":true,\"blockers\":[]}", true));
    }

    @Test
    @DisplayName("탈퇴하면 목 예시 모양 그대로 isDeleted와 한국 시각 deletedAt을 돌려준다")
    void withdrawMatchesMockExample() throws Exception {
        when(withdrawalService.withdraw(12)).thenReturn(
                new WithdrawalResultResponse(true, OffsetDateTime.parse("2026-09-14T10:05:00.123456+09:00")));

        mockMvc.perform(withToken(post(WITHDRAWAL)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"isDeleted":true,"deletedAt":"2026-09-14T10:05:00+09:00"}
                        """, true));
    }

    @Test
    @DisplayName("진행 중인 의뢰나 남은 돈이 있으면 409 WITHDRAWAL_BLOCKED다")
    void blockedWithdrawalIsConflict() throws Exception {
        when(withdrawalService.withdraw(12)).thenThrow(new BusinessException(MyPageErrorCode.WITHDRAWAL_BLOCKED));

        mockMvc.perform(withToken(post(WITHDRAWAL)))
                .andExpect(status().isConflict())
                .andExpect(content().json("""
                        {"code":"WITHDRAWAL_BLOCKED","message":"진행 중인 의뢰나 남은 돈이 있어 탈퇴할 수 없습니다."}
                        """, true));
    }

    @Test
    @DisplayName("토큰이 없으면 이름 바꾸기·탈퇴 전 확인·탈퇴 모두 401이고 서비스는 불리지 않는다")
    void accountApisRequireToken() throws Exception {
        mockMvc.perform(patch(ME).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"김미미\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}", true));
        mockMvc.perform(get(WITHDRAWAL)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(WITHDRAWAL)).andExpect(status().isUnauthorized());

        verify(myAccountService, never()).changeName(any(), any());
        verify(withdrawalService, never()).check(any());
        verify(withdrawalService, never()).withdraw(any());
    }

    private void expectNameError(String body, String reason) throws Exception {
        mockMvc.perform(withToken(patch(ME)).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"name","reason":"%s"}]}
                        """.formatted(reason), true));
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request) {
        return request
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                .contentType(MediaType.APPLICATION_JSON);
    }

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
