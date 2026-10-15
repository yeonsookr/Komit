package com.a501.service.domain.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.review.ReviewErrorCode;
import com.a501.service.domain.review.dto.response.ReviewResponse;
import com.a501.service.domain.review.service.ReviewCommandService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 목 명세 {@code PUT /api/reviews/{reviewId}/reply}와 같은 계약인지 봅니다. 기대 JSON은 목 {@code MY_DEVELOPER_PROFILE_EXAMPLE["reviews"][0]}입니다. */
@WebMvcSecurityTest(ReviewReplyController.class)
class ReviewReplyControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private ReviewCommandService reviewCommandService;

    @Test
    @DisplayName("답글을 달면 답글이 담긴 리뷰 한 건을 돌려준다")
    void replyReturnsReview() throws Exception {
        when(reviewCommandService.reply(12, 88, "감사합니다.")).thenReturn(new ReviewResponse(
                88, "김O미", "DEVELOPMENT", 5, 5, "말이 잘 통했습니다.", "감사합니다.",
                OffsetDateTime.parse("2026-08-19T14:20:00+09:00")));

        mockMvc.perform(authenticated(put("/api/reviews/88/reply"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\":\"감사합니다.\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"reviewId":88,"clientName":"김O미","contractType":"DEVELOPMENT","devScore":5,"expScore":5,
                         "content":"말이 잘 통했습니다.","reply":"감사합니다.","createdAt":"2026-08-19T14:20:00+09:00"}
                        """, true));
    }

    @Test
    @DisplayName("남의 리뷰는 403 FORBIDDEN, 없는 리뷰는 404 REVIEW_NOT_FOUND다")
    void forbiddenAndNotFound() throws Exception {
        when(reviewCommandService.reply(12, 87, "x")).thenThrow(new BusinessException(CommonErrorCode.FORBIDDEN));
        when(reviewCommandService.reply(12, 404, "x")).thenThrow(new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));

        mockMvc.perform(authenticated(put("/api/reviews/87/reply"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}", true));
        mockMvc.perform(authenticated(put("/api/reviews/404/reply"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"code\":\"REVIEW_NOT_FOUND\",\"message\":\"리뷰를 찾을 수 없습니다.\"}", true));
    }

    @Test
    @DisplayName("reply가 비었거나 1000자를 넘거나 없으면 목과 같은 문구로 422다")
    void invalidReplyIsInvalidRequest() throws Exception {
        mockMvc.perform(authenticated(put("/api/reviews/88/reply"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"reply","reason":"1자 이상이어야 합니다."}]}
                        """, true));
        mockMvc.perform(authenticated(put("/api/reviews/88/reply"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\":\"" + "가".repeat(1001) + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"reply","reason":"1000자 이하여야 합니다."}]}
                        """, true));
        mockMvc.perform(authenticated(put("/api/reviews/88/reply"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"reply","reason":"필수 항목입니다."}]}
                        """, true));

        verify(reviewCommandService, never()).reply(anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("토큰이 없으면 401 UNAUTHORIZED이고 서비스를 부르지 않는다")
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/reviews/88/reply")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\":\"감사합니다.\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}", true));

        verify(reviewCommandService, never()).reply(anyInt(), anyInt(), any());
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12));
    }
}
