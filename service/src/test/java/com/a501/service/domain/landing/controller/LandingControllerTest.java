package com.a501.service.domain.landing.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.developer.dto.response.TaskPriceResponse;
import com.a501.service.domain.landing.dto.response.FeaturedDeveloperListResponse;
import com.a501.service.domain.landing.dto.response.FeaturedDeveloperResponse;
import com.a501.service.domain.landing.service.LandingQueryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 목 명세 {@code GET /api/landing/featured-developers}와 같은 계약인지 봅니다. 기대 JSON은 목 {@code FEATURED_EXAMPLE}입니다. */
@WebMvcSecurityTest(LandingController.class)
class LandingControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private LandingQueryService landingQueryService;

    @Test
    @DisplayName("추천 개발자는 토큰 없이 부를 수 있고 기본 3명을 요청하며 목 예시와 같은 모양이다")
    void featuredDevelopersIsPublicAndMatchesContract() throws Exception {
        when(landingQueryService.getFeaturedDevelopers(3)).thenReturn(new FeaturedDeveloperListResponse(List.of(
                new FeaturedDeveloperResponse(3, "이준", null, null, "간단한 상담도 받습니다.", new BigDecimal("5.0"),
                        new CodeResponse(42, "DB 연결"), 4.4, 4.9,
                        List.of(new TaskPriceResponse("CONSULT", 50000L), new TaskPriceResponse("DEVELOP", 100000L))))));

        mockMvc.perform(get("/api/landing/featured-developers"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"content":[{"developerId":3,"name":"이준","profileImageFileId":null,"profileImageUrl":null,"intro":"간단한 상담도 받습니다.",
                                     "yearsOfExperience":5.0,"field":{"codeId":42,"codeName":"DB 연결"},
                                     "devRating":4.4,"explanatoryRating":4.9,
                                     "prices":[{"taskType":"CONSULT","price":50000},{"taskType":"DEVELOP","price":100000}]}]}
                        """, true));
    }

    @Test
    @DisplayName("평점·연차·분야가 없으면 null이고 노출할 개발자가 없으면 content가 빈 배열이다")
    void nullsAndEmptyList() throws Exception {
        when(landingQueryService.getFeaturedDevelopers(1)).thenReturn(new FeaturedDeveloperListResponse(List.of(
                new FeaturedDeveloperResponse(9, "새내기", 77, "https://cdn.komit.example.com/profile/77.png", "처음입니다.", null, null, null, null, List.of()))));
        when(landingQueryService.getFeaturedDevelopers(10)).thenReturn(new FeaturedDeveloperListResponse(List.of()));

        mockMvc.perform(get("/api/landing/featured-developers").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"content":[{"developerId":9,"name":"새내기","profileImageFileId":77,"profileImageUrl":"https://cdn.komit.example.com/profile/77.png","intro":"처음입니다.",
                                     "yearsOfExperience":null,"field":null,"devRating":null,"explanatoryRating":null,"prices":[]}]}
                        """, true));
        mockMvc.perform(get("/api/landing/featured-developers").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"content\":[]}", true));
    }

    @Test
    @DisplayName("size가 1 미만이거나 10 초과면 목의 FEATURED_SIZE_ERROR_EXAMPLE과 같은 422다")
    void sizeOutOfRangeIsInvalidRequest() throws Exception {
        mockMvc.perform(get("/api/landing/featured-developers").param("size", "11"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"size","reason":"10 이하여야 합니다."}]}
                        """, true));
        mockMvc.perform(get("/api/landing/featured-developers").param("size", "0"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"size","reason":"1 이상이어야 합니다."}]}
                        """, true));

        verify(landingQueryService, never()).getFeaturedDevelopers(anyInt());
    }
}
