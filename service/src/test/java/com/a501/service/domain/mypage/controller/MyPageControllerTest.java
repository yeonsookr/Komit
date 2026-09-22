package com.a501.service.domain.mypage.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.account.dto.response.AccountResponse;
import com.a501.service.domain.mypage.dto.response.MyAccountResponse;
import com.a501.service.domain.mypage.dto.response.MyDeveloperResponse;
import com.a501.service.domain.mypage.dto.response.MyPageResponse;
import com.a501.service.domain.mypage.dto.response.MyProjectResponse;
import com.a501.service.domain.mypage.dto.response.NotificationSettingResponse;
import com.a501.service.domain.mypage.service.MyPageService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 목 명세 {@code GET /api/mypage}와 같은 계약인지 봅니다. */
@WebMvcSecurityTest(MyPageController.class)
class MyPageControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private MyPageService myPageService;

    @Test
    @DisplayName("로그인한 사용자의 마이페이지 한 판을 목 계약 그대로 반환한다")
    void authenticatedUserGetsMyPage() throws Exception {
        when(myPageService.getMyPage(12)).thenReturn(new MyPageResponse(
                new MyAccountResponse("김미미", "mimi@kakao.com", null),
                new AccountResponse(22, "신한은행", "···· 4412", "김미미"),
                List.of(new MyProjectResponse(
                        12,
                        "수제 비누 가게",
                        31,
                        OffsetDateTime.parse("2026-08-17T09:12:00+09:00"),
                        4)),
                new MyDeveloperResponse(3, 12, 1),
                new NotificationSettingResponse(true)));

        mockMvc.perform(get("/api/mypage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "account": {
                            "name": "김미미",
                            "email": "mimi@kakao.com",
                            "profileImageUrl": null
                          },
                          "settlementAccount": {
                            "accountId": 22,
                            "bankName": "신한은행",
                            "maskedNumber": "···· 4412",
                            "accountHolder": "김미미"
                          },
                          "projects": [
                            {
                              "projectId": 12,
                              "name": "수제 비누 가게",
                              "latestDiagnosisId": 31,
                              "latestDiagnosedAt": "2026-08-17T09:12:00+09:00",
                              "roomCount": 4
                            }
                          ],
                          "developer": {
                            "developerId": 3,
                            "reviewCount": 12,
                            "upcomingSessionCount": 1
                          },
                          "notificationSetting": {
                            "notificationEnabled": true
                          }
                        }
                        """, true));
    }

    @Test
    @DisplayName("토큰이 없으면 401 UNAUTHORIZED이고 서비스를 부르지 않는다")
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/mypage"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}
                        """, true));

        verify(myPageService, never()).getMyPage(any());
    }
}
