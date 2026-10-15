package com.a501.service.domain.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.review.ReviewErrorCode;
import com.a501.service.domain.review.dto.request.WriteReviewRequest;
import com.a501.service.domain.review.dto.response.ReviewResponse;
import com.a501.service.domain.review.service.ReviewQueryService;
import com.a501.service.domain.review.service.ReviewWriteService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 목 명세 {@code POST /api/contracts/{contractId}/review}와 같은 계약인지 봅니다. */
@WebMvcSecurityTest(ContractReviewController.class)
class ContractReviewControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private ReviewWriteService reviewWriteService;
    @MockitoBean
    private ReviewQueryService reviewQueryService;

    @Test
    @DisplayName("이 계약에 남긴 리뷰를 돌려준다")
    void returnsTheReviewOfTheContract() throws Exception {
        when(reviewQueryService.findByContract(12, 130)).thenReturn(new ReviewResponse(
                88, "김O미", "CONSULTING", 5, 4, "말이 잘 통했습니다.", "감사합니다.",
                OffsetDateTime.parse("2026-08-19T14:20:00+09:00")));

        mockMvc.perform(authenticated(get("/api/contracts/130/review")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"reviewId":88,"clientName":"김O미","contractType":"CONSULTING","devScore":5,"expScore":4,
                         "content":"말이 잘 통했습니다.","reply":"감사합니다.","createdAt":"2026-08-19T14:20:00+09:00"}
                        """, true));
    }

    @Test
    @DisplayName("리뷰가 아직 없으면 REVIEW_NOT_FOUND, 내 계약이 아니면 CONTRACT_NOT_FOUND 로 404 다")
    void notFoundCases() throws Exception {
        when(reviewQueryService.findByContract(12, 131))
                .thenThrow(new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));
        when(reviewQueryService.findByContract(12, 132))
                .thenThrow(new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/contracts/131/review")))
                .andExpect(status().isNotFound())
                .andExpect(content().json("""
                        {"code":"REVIEW_NOT_FOUND","message":"리뷰를 찾을 수 없습니다."}
                        """, true));
        mockMvc.perform(authenticated(get("/api/contracts/132/review")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
    }

    @Test
    @DisplayName("남기면 201 과 리뷰 한 건을 돌려준다")
    void returnsTheWrittenReview() throws Exception {
        when(reviewWriteService.write(12, 130, new WriteReviewRequest(5, 4, "말이 잘 통했습니다.")))
                .thenReturn(new ReviewResponse(88, "김O미", "CONSULTING", 5, 4, "말이 잘 통했습니다.", null,
                        OffsetDateTime.parse("2026-08-19T14:20:00+09:00")));

        mockMvc.perform(authenticated(post("/api/contracts/130/review"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"devScore\":5,\"expScore\":4,\"content\":\"말이 잘 통했습니다.\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"reviewId":88,"clientName":"김O미","contractType":"CONSULTING","devScore":5,"expScore":4,
                         "content":"말이 잘 통했습니다.","reply":null,"createdAt":"2026-08-19T14:20:00+09:00"}
                        """, true));
    }

    @Test
    @DisplayName("끝나지 않은 거래는 400, 이미 남겼으면 409 다")
    void rejectsUnfinishedAndDuplicate() throws Exception {
        when(reviewWriteService.write(eq(12), eq(131), any()))
                .thenThrow(new BusinessException(ReviewErrorCode.CONTRACT_NOT_COMPLETED));
        when(reviewWriteService.write(eq(12), eq(132), any()))
                .thenThrow(new BusinessException(ReviewErrorCode.REVIEW_ALREADY_WRITTEN));

        mockMvc.perform(authenticated(post("/api/contracts/131/review"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"devScore\":5,\"expScore\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"CONTRACT_NOT_COMPLETED","message":"계약이 끝나야 리뷰를 남길 수 있습니다."}
                        """, true));
        mockMvc.perform(authenticated(post("/api/contracts/132/review"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"devScore\":5,\"expScore\":5}"))
                .andExpect(status().isConflict())
                .andExpect(content().json("""
                        {"code":"REVIEW_ALREADY_WRITTEN","message":"이미 리뷰를 남기셨습니다."}
                        """, true));
    }

    @Test
    @DisplayName("점수가 1~5 밖이면 서비스를 부르지 않고 422 다")
    void rejectsScoreOutOfRange() throws Exception {
        mockMvc.perform(authenticated(post("/api/contracts/130/review"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"devScore\":6,\"expScore\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(reviewWriteService, never()).write(any(), any(), any());
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12));
    }
}
