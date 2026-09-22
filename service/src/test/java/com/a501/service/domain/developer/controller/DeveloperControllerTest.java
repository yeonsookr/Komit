package com.a501.service.domain.developer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.developer.DeveloperErrorCode;
import com.a501.service.domain.developer.dto.request.DeveloperSort;
import com.a501.service.domain.developer.dto.request.SearchMode;
import com.a501.service.domain.developer.dto.response.CareerResponse;
import com.a501.service.domain.developer.dto.response.DeveloperDetailResponse;
import com.a501.service.domain.developer.dto.response.DeveloperListResponse;
import com.a501.service.domain.developer.dto.response.DeveloperSummaryResponse;
import com.a501.service.domain.developer.dto.response.PortfolioItemResponse;
import com.a501.service.domain.developer.dto.response.PriceCatalogItemResponse;
import com.a501.service.domain.developer.dto.response.RatingResponse;
import com.a501.service.domain.developer.dto.response.TaskPriceResponse;
import com.a501.service.domain.developer.service.DeveloperQueryService;
import com.a501.service.domain.review.dto.response.ReviewListResponse;
import com.a501.service.domain.review.dto.response.ReviewResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 목 명세 {@code 3 개발자 찾기 · 상담방}의 {@code /api/developers} 세 엔드포인트와 같은 계약인지 봅니다.
 * 기대 JSON은 목 서버의 {@code DEVELOPERS[0]}·{@code DEVELOPER_DETAIL}·{@code REVIEWS} 예시를 옮긴 것이고, 세 엔드포인트는 토큰 없이 부릅니다.
 */
@WebMvcSecurityTest(DeveloperController.class)
class DeveloperControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private DeveloperQueryService developerQueryService;

    private static final List<CodeResponse> TECH_CODES = List.of(
            new CodeResponse(21, "Next.js"), new CodeResponse(22, "Supabase"), new CodeResponse(23, "TypeScript"));

    static DeveloperSummaryResponse summary() {
        return new DeveloperSummaryResponse(
                3, "이준", null, null,
                "간단한 상담도 받습니다. 용어부터 풀어서 설명합니다.",
                new CodeResponse(42, "DB 연결"),
                new BigDecimal("5.0"),
                true,
                RatingResponse.of(4.4, 2), RatingResponse.of(4.9, 2),
                TECH_CODES,
                List.of(new TaskPriceResponse("CONSULT", 50000L), new TaskPriceResponse("DEVELOP", 100000L)),
                23);
    }

    private static final String SUMMARY_JSON = """
            {"developerId":3,"name":"이준","profileImageFileId":null,"profileImageUrl":null,
             "intro":"간단한 상담도 받습니다. 용어부터 풀어서 설명합니다.",
             "field":{"codeId":42,"codeName":"DB 연결"},"yearsOfExperience":5.0,"isCareerVerified":true,
             "devRating":{"score":4.4,"reviewCount":2},"explanatoryRating":{"score":4.9,"reviewCount":2},
             "techCategories":[{"codeId":21,"codeName":"Next.js"},{"codeId":22,"codeName":"Supabase"},{"codeId":23,"codeName":"TypeScript"}],
             "prices":[{"taskType":"CONSULT","price":50000},{"taskType":"DEVELOP","price":100000}],
             "contractCount":23}
            """;

    @Test
    @DisplayName("개발자 찾기는 토큰 없이 부를 수 있고 목 예시와 같은 목록 모양이다")
    void findDevelopersIsPublicAndMatchesContract() throws Exception {
        when(developerQueryService.search(SearchMode.PROBLEM, null, null, DeveloperSort.DEV_RATING_DESC, 1, 20))
                .thenReturn(new DeveloperListResponse(List.of(summary()), 1, 20, 1, 1, 128));

        mockMvc.perform(get("/api/developers"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"content\":[" + SUMMARY_JSON + "],"
                        + "\"page\":1,\"size\":20,\"totalElements\":1,\"totalPages\":1,\"totalDevelopers\":128}", true));
    }

    @Test
    @DisplayName("검색 방식·검색어·분야·정렬·페이지를 목과 같은 이름으로 받고 fieldCodeIds는 같은 이름을 거듭 적는다")
    void findDevelopersPassesParameters() throws Exception {
        when(developerQueryService.search(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(DeveloperListResponse.empty(2, 5, 128));

        mockMvc.perform(get("/api/developers")
                        .param("searchMode", "name").param("keyword", "이준")
                        .param("fieldCodeIds", "41").param("fieldCodeIds", "42")
                        .param("sort", "price,asc").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"content":[],"page":2,"size":5,"totalElements":0,"totalPages":0,"totalDevelopers":128}
                        """, true));

        verify(developerQueryService).search(SearchMode.NAME, "이준", List.of(41, 42), DeveloperSort.PRICE_ASC, 2, 5);
    }

    @Test
    @DisplayName("searchMode·sort가 허용 값이 아니면 목의 literal 문구로 422다. 상수 이름과 공백 섞인 값도 거절한다")
    void unsupportedLiteralIsInvalidRequest() throws Exception {
        mockMvc.perform(get("/api/developers").param("searchMode", "PROBLEM"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"searchMode","reason":"'problem' or 'name' 중 하나여야 합니다."}]}
                        """, true));
        for (String value : List.of("DEV_RATING_DESC", "devRating,asc", "price,asc ", "contract,desc  ")) {
            mockMvc.perform(get("/api/developers").param("sort", value))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors.length()").value(1))
                    .andExpect(jsonPath("$.errors[0].field").value("sort"))
                    .andExpect(jsonPath("$.errors[0].reason").value(
                            "'devRating,desc', 'explanatoryRating,desc', 'price,asc' or 'contract,desc' 중 하나여야 합니다."));
        }

        verify(developerQueryService, never()).search(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("page·size 범위 위반은 목과 같은 문구로 필드별로 나열한다")
    void pageAndSizeOutOfRangeAreListed() throws Exception {
        mockMvc.perform(get("/api/developers").param("page", "0").param("size", "101"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"page","reason":"1 이상이어야 합니다."},
                                   {"field":"size","reason":"100 이하여야 합니다."}]}
                        """, true));
        mockMvc.perform(get("/api/developers").param("fieldCodeIds", "abc"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("fieldCodeIds"));

        verify(developerQueryService, never()).search(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("프로필 조회는 목록 값에 소개글·활동 시간대·경력·작업물·가격표를 더한 목 예시 모양이다")
    void getDeveloperMatchesContract() throws Exception {
        when(developerQueryService.getDetail(3)).thenReturn(new DeveloperDetailResponse(
                3, "이준", null, null, "간단한 상담도 받습니다. 용어부터 풀어서 설명합니다.",
                new CodeResponse(42, "DB 연결"), new BigDecimal("5.0"), true,
                RatingResponse.of(4.4, 2), RatingResponse.of(4.9, 2), TECH_CODES,
                List.of(new TaskPriceResponse("CONSULT", 50000L), new TaskPriceResponse("DEVELOP", 100000L)), 23,
                "작은 가게 서비스를 주로 맡습니다.", LocalTime.of(10, 0), LocalTime.of(19, 0),
                List.of(new CareerResponse(41, "카카오스타일", "백엔드 · 주문/정산 서비스", LocalDate.of(2021, 3, 1),
                                LocalDate.of(2024, 8, 31), true, OffsetDateTime.parse("2026-03-04T10:00:00+09:00")),
                        new CareerResponse(42, "프리랜서", "소상공인 웹 서비스 14건", LocalDate.of(2024, 9, 1), null, false, null)),
                List.of(new PortfolioItemResponse(7, "동네 필라테스 수업", null, "https://example.com", null, null)),
                List.of(new PriceCatalogItemResponse(60, "CONSULT", "30분 · 한 가지 주제", 50000L, null),
                        new PriceCatalogItemResponse(62, "DEVELOP", "인터넷에 올리기", 200000L, 1))));

        mockMvc.perform(get("/api/developers/3"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"developerId":3,"name":"이준","profileImageFileId":null,"profileImageUrl":null,
                         "intro":"간단한 상담도 받습니다. 용어부터 풀어서 설명합니다.",
                         "field":{"codeId":42,"codeName":"DB 연결"},"yearsOfExperience":5.0,"isCareerVerified":true,
                         "devRating":{"score":4.4,"reviewCount":2},"explanatoryRating":{"score":4.9,"reviewCount":2},
                         "techCategories":[{"codeId":21,"codeName":"Next.js"},{"codeId":22,"codeName":"Supabase"},{"codeId":23,"codeName":"TypeScript"}],
                         "prices":[{"taskType":"CONSULT","price":50000},{"taskType":"DEVELOP","price":100000}],
                         "contractCount":23,
                         "description":"작은 가게 서비스를 주로 맡습니다.","activeFrom":"10:00","activeTo":"19:00",
                         "careers":[{"careerId":41,"company":"카카오스타일","role":"백엔드 · 주문/정산 서비스","startDate":"2021-03-01",
                                     "endDate":"2024-08-31","isVerified":true,"verifiedAt":"2026-03-04T10:00:00+09:00"},
                                    {"careerId":42,"company":"프리랜서","role":"소상공인 웹 서비스 14건","startDate":"2024-09-01",
                                     "endDate":null,"isVerified":false,"verifiedAt":null}],
                         "portfolioItems":[{"portfolioItemId":7,"title":"동네 필라테스 수업","description":null,
                                            "linkUrl":"https://example.com","thumbnailFileId":null,"thumbnailUrl":null}],
                         "priceCatalog":[{"priceId":60,"taskType":"CONSULT","taskName":"30분 · 한 가지 주제","price":50000,"periodDays":null},
                                         {"priceId":62,"taskType":"DEVELOP","taskName":"인터넷에 올리기","price":200000,"periodDays":1}]}
                        """, true));
    }

    @Test
    @DisplayName("없거나 탈퇴한 개발자는 404 DEVELOPER_NOT_FOUND다 (목의 developerId=404 사례)")
    void missingDeveloperIsNotFound() throws Exception {
        when(developerQueryService.getDetail(404)).thenThrow(new BusinessException(DeveloperErrorCode.DEVELOPER_NOT_FOUND));
        when(developerQueryService.getReviews(404, 1, 20)).thenThrow(new BusinessException(DeveloperErrorCode.DEVELOPER_NOT_FOUND));

        mockMvc.perform(get("/api/developers/404"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"code\":\"DEVELOPER_NOT_FOUND\",\"message\":\"개발자를 찾을 수 없습니다.\"}", true));
        mockMvc.perform(get("/api/developers/404/reviews"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"code\":\"DEVELOPER_NOT_FOUND\",\"message\":\"개발자를 찾을 수 없습니다.\"}", true));
        mockMvc.perform(get("/api/developers/abc"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("developerId"))
                .andExpect(jsonPath("$.errors[0].reason").value("정수여야 합니다."));
    }

    @Test
    @DisplayName("리뷰 목록은 기준 API의 목록 모양이고 page·size 기본값은 1·20이다")
    void getDeveloperReviewsMatchesContract() throws Exception {
        when(developerQueryService.getReviews(3, 1, 20)).thenReturn(new ReviewListResponse(List.of(
                new ReviewResponse(88, "김O미", "DEVELOPMENT", 5, 5, "말이 잘 통했습니다.", null,
                        OffsetDateTime.parse("2026-08-19T14:20:00+09:00")),
                new ReviewResponse(87, "최O진", "CONSULTING", 4, 5, null, "감사합니다.",
                        OffsetDateTime.parse("2026-08-02T09:10:00+09:00"))), 1, 20, 2, 1));

        mockMvc.perform(get("/api/developers/3/reviews"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"content":[{"reviewId":88,"clientName":"김O미","contractType":"DEVELOPMENT","devScore":5,"expScore":5,
                                     "content":"말이 잘 통했습니다.","reply":null,"createdAt":"2026-08-19T14:20:00+09:00"},
                                    {"reviewId":87,"clientName":"최O진","contractType":"CONSULTING","devScore":4,"expScore":5,
                                     "content":null,"reply":"감사합니다.","createdAt":"2026-08-02T09:10:00+09:00"}],
                         "page":1,"size":20,"totalElements":2,"totalPages":1}
                        """, true));

        mockMvc.perform(get("/api/developers/3/reviews").param("page", "0").param("size", "101"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(2));
        verify(developerQueryService, never()).getReviews(eq(3), eq(0), anyInt());
    }
}
