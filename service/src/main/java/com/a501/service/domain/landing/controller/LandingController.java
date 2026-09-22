package com.a501.service.domain.landing.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.domain.landing.dto.response.FeaturedDeveloperListResponse;
import com.a501.service.domain.landing.service.LandingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 랜딩 API. 목 명세 {@code 1 랜딩 · 로그인} 태그의 {@code /api/landing} 아래 엔드포인트입니다. 로그인하지 않은 방문자도 부르므로
 * {@code SecurityConfig}가 GET을 열어 두고, 인증 사용자를 받지 않습니다.
 *
 * <p>{@code GET /api/landing/summary}(이번 주 상담 수)는 방 도메인의 집계라 이 작업에 넣지 않았습니다.
 */
@Tag(name = "1 랜딩 · 로그인", description = "로그인하지 않은 방문자도 보는 랜딩 정보")
@RestController
@RequestMapping("/api/landing")
@RequiredArgsConstructor
public class LandingController {
    private final LandingQueryService landingQueryService;

    @Operation(
            summary = "추천 개발자 목록 조회",
            description = "랜딩의 '추천 개발자' 카드에 쓰는 목록이다. 로그인하지 않은 방문자도 보는 공개 정보라 표시 이름과 프로필 사진까지만 담는다. "
                    + "카드에는 무료 상담 가격이 따로 표시되지 않는다.\n\n"
                    + "- **200**: 항상. 노출할 개발자가 없으면 `content`가 빈 배열이다. 평점은 리뷰가 없으면 null이고, 경력 연차와 분야도 등록 전이면 null이다\n"
                    + "- **422 / INVALID_REQUEST**: `size`가 1 미만이거나 10 초과일 때\n"
                    + "- **500 / INTERNAL_ERROR**: 목록을 만들지 못했을 때")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/featured-developers")
    FeaturedDeveloperListResponse getFeaturedDevelopers(
            @Parameter(description = "내려받을 개발자 수. 랜딩은 3명을 쓴다.", example = "3")
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int size) {
        return landingQueryService.getFeaturedDevelopers(size);
    }
}
