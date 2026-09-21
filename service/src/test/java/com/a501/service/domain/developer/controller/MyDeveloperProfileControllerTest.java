package com.a501.service.domain.developer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.developer.DeveloperErrorCode;
import com.a501.service.domain.developer.dto.response.MyCareerResponse;
import com.a501.service.domain.developer.dto.response.MyDeveloperProfileResponse;
import com.a501.service.domain.developer.dto.response.MyPortfolioItemResponse;
import com.a501.service.domain.developer.dto.response.PriceCatalogItemResponse;
import com.a501.service.domain.developer.dto.response.RatingResponse;
import com.a501.service.domain.developer.service.DeveloperProfileCommandService;
import com.a501.service.domain.developer.service.DeveloperProfileQueryService;
import com.a501.service.domain.developer.service.DeveloperProfileSaveResult;
import com.a501.service.domain.review.dto.response.ReviewResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 목 명세 {@code /api/users/me/developer-profile}와 같은 계약인지 봅니다. 응답 예시는 목 {@code MY_DEVELOPER_PROFILE_EXAMPLE}을 줄인 것입니다. */
@WebMvcSecurityTest(MyDeveloperProfileController.class)
class MyDeveloperProfileControllerTest {
    private static final String PATH = "/api/users/me/developer-profile";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private DeveloperProfileQueryService queryService;
    @MockitoBean
    private DeveloperProfileCommandService commandService;

    private static final String EXPECTED_JSON = """
            {"developerId":3,"name":"이준","profileImageUrl":null,
             "intro":"간단한 상담도 받습니다. 용어부터 풀어서 설명합니다.",
             "description":"작은 가게 서비스를 주로 맡습니다.","yearsOfExperience":5.0,
             "field":{"codeId":42,"codeName":"DB 연결"},"activeFrom":"10:00","activeTo":"19:00",
             "isCareerVerified":true,
             "devRating":{"score":4.8,"reviewCount":2},"explanatoryRating":{"score":4.9,"reviewCount":2},
             "techCategories":[{"codeId":23,"codeName":"TypeScript"}],
             "careers":[{"careerId":41,"company":"카카오스타일","role":"백엔드 · 주문/정산 서비스",
                         "startDate":"2021-03-01","endDate":"2024-08-31","isVerified":true,
                         "verifiedAt":"2026-03-04T10:00:00+09:00","evidenceFileId":null,"evidenceFileName":null}],
             "portfolioItems":[{"portfolioItemId":7,"title":"미미헤어샵 예약","description":"예약이 저장되지 않던 문제를 고쳤습니다.",
                                "linkUrl":"https://hairshop-mimi.com","thumbnailFileId":512,"thumbnailUrl":null},
                               {"portfolioItemId":9,"title":"사내 재고 관리","description":null,"linkUrl":null,
                                "thumbnailFileId":null,"thumbnailUrl":null}],
             "priceCatalog":[{"priceId":60,"taskType":"CONSULT","taskName":"30분 · 한 가지 주제","price":50000,"periodDays":null},
                             {"priceId":62,"taskType":"DEVELOP","taskName":"인터넷에 올리기","price":200000,"periodDays":1}],
             "reviews":[{"reviewId":88,"clientName":"김O미","contractType":"DEVELOPMENT","devScore":5,"expScore":5,
                         "content":"말이 잘 통했습니다.","reply":"감사합니다.","createdAt":"2026-08-19T14:20:00+09:00"}],
             "replyCount":1,"updatedAt":"2026-08-14T18:20:00+09:00"}
            """;

    private static final String VALID_REQUEST = """
            {"intro":"간단한 상담도 받습니다.","description":null,"fieldCodeId":42,"yearsOfExperience":5.0,
             "activeFrom":"10:00","activeTo":"19:00","techCodeIds":[21,23],
             "careers":[{"careerId":null,"company":"카카오스타일","role":"백엔드","startDate":"2021-03-01","endDate":"2024-08-31","evidenceFileId":null}],
             "portfolioItems":[{"portfolioItemId":null,"title":"미미헤어샵 예약","description":null,"linkUrl":null,"thumbnailFileId":null}],
             "prices":[{"priceId":null,"taskType":"DEVELOP","taskName":"로그인 붙이기","price":400000,"periodDays":4}]}
            """;

    static MyDeveloperProfileResponse example() {
        return new MyDeveloperProfileResponse(
                3, "이준", null,
                "간단한 상담도 받습니다. 용어부터 풀어서 설명합니다.",
                "작은 가게 서비스를 주로 맡습니다.",
                new BigDecimal("5.0"),
                new CodeResponse(42, "DB 연결"),
                LocalTime.of(10, 0), LocalTime.of(19, 0),
                true,
                RatingResponse.of(4.8, 2), RatingResponse.of(4.9, 2),
                List.of(new CodeResponse(23, "TypeScript")),
                List.of(new MyCareerResponse(41, "카카오스타일", "백엔드 · 주문/정산 서비스",
                        LocalDate.of(2021, 3, 1), LocalDate.of(2024, 8, 31), true,
                        OffsetDateTime.parse("2026-03-04T10:00:00+09:00"), null, null)),
                List.of(new MyPortfolioItemResponse(7, "미미헤어샵 예약", "예약이 저장되지 않던 문제를 고쳤습니다.",
                                "https://hairshop-mimi.com", 512, null),
                        new MyPortfolioItemResponse(9, "사내 재고 관리", null, null, null, null)),
                List.of(new PriceCatalogItemResponse(60, "CONSULT", "30분 · 한 가지 주제", 50000, null),
                        new PriceCatalogItemResponse(62, "DEVELOP", "인터넷에 올리기", 200000, 1)),
                List.of(new ReviewResponse(88, "김O미", "DEVELOPMENT", 5, 5, "말이 잘 통했습니다.", "감사합니다.",
                        OffsetDateTime.parse("2026-08-19T14:20:00+09:00"))),
                1,
                OffsetDateTime.parse("2026-08-14T18:20:00+09:00"));
    }

    @Test
    void getReturnsTheContractShape() throws Exception {
        when(queryService.getMine(12)).thenReturn(example());

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12)))
                .andExpect(status().isOk())
                .andExpect(content().json(EXPECTED_JSON, true));
    }

    @Test
    void getWithoutProfileIsForbiddenWithDomainCode() throws Exception {
        when(queryService.getMine(12)).thenThrow(new BusinessException(DeveloperErrorCode.NO_DEVELOPER_PROFILE));

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12)))
                .andExpect(status().isForbidden())
                .andExpect(content().json("""
                        {"code":"NO_DEVELOPER_PROFILE","message":"개발자 프로필이 없습니다."}
                        """, true));
    }

    @Test
    void putReturns201WhenProfileWasCreatedAnd200WhenUpdated() throws Exception {
        when(commandService.save(eq(12), any())).thenReturn(new DeveloperProfileSaveResult(true, example()));
        mockMvc.perform(put(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(content().json(EXPECTED_JSON, true));

        when(commandService.save(eq(12), any())).thenReturn(new DeveloperProfileSaveResult(false, example()));
        mockMvc.perform(put(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isOk());
    }

    @Test
    void putRejectsEmptyIntroZeroPriceReversedPeriodAndLongTitle() throws Exception {
        String body = """
                {"intro":"","description":null,"fieldCodeId":null,"yearsOfExperience":null,"activeFrom":null,"activeTo":null,
                 "techCodeIds":[],
                 "careers":[{"careerId":null,"company":"A","role":"B","startDate":"2024-01-01","endDate":"2023-12-31","evidenceFileId":null}],
                 "portfolioItems":[{"portfolioItemId":null,"title":"%s","description":null,"linkUrl":null,"thumbnailFileId":null}],
                 "prices":[{"priceId":null,"taskType":"CONSULT","taskName":"30분","price":0,"periodDays":null}]}
                """.formatted("가".repeat(201));

        mockMvc.perform(put(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"careers.0.endDate","reason":"시작일보다 빠를 수 없습니다."},
                                   {"field":"intro","reason":"1자 이상이어야 합니다."},
                                   {"field":"portfolioItems.0.title","reason":"200자 이하여야 합니다."},
                                   {"field":"prices.0.price","reason":"1 이상이어야 합니다."}]}
                        """, true));

        verify(commandService, never()).save(any(), any());
    }

    @Test
    void putRejectsUnknownTaskTypeWithAllowedList() throws Exception {
        String body = VALID_REQUEST.replace("\"DEVELOP\"", "\"FREE\"");

        mockMvc.perform(put(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"prices.0.taskType","reason":"'CONSULT' or 'DEVELOP' 중 하나여야 합니다."}]}
                        """, true));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}
                        """, true));

        verify(queryService, never()).getMine(any());
    }
}
