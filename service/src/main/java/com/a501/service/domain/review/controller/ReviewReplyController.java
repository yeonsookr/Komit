package com.a501.service.domain.review.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.review.dto.request.WriteReviewReplyRequest;
import com.a501.service.domain.review.dto.response.ReviewResponse;
import com.a501.service.domain.review.service.ReviewCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 리뷰 답글. 목 명세 {@code 8 계정 · 공통} 태그의 {@code PUT /api/reviews/{reviewId}/reply}입니다. */
@Tag(name = "8 계정 · 공통", description = "내 개발자 프로필의 받은 리뷰에 답글을 남긴다")
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewReplyController {
    private final ReviewCommandService reviewCommandService;

    @Operation(
            summary = "리뷰 답글 쓰기 · 고치기",
            description = "받은 리뷰에 답글을 단다. 리뷰 하나에 답글은 하나뿐이라 다시 보내면 덮어쓴다. 프로필 저장과 따로 움직이며 보내는 즉시 남는다. "
                    + "답글은 의뢰인에게 보이는 프로필에도 함께 나간다.\n\n"
                    + "리뷰를 받은 개발자가 토큰의 주인인지 검증한 뒤에 저장하고, 다르면 403으로 막는다.\n\n"
                    + "- **200**: 달았거나 고쳤을 때\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **403 / FORBIDDEN**: 토큰의 주인이 받은 리뷰가 아닐 때\n"
                    + "- **404 / REVIEW_NOT_FOUND**: 리뷰가 없거나 삭제되었을 때. 목은 `reviewId`=404로 재현한다\n"
                    + "- **422 / INVALID_REQUEST**: `reply`가 비었거나 1000자를 넘을 때\n"
                    + "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "달았거나 고쳤음"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "토큰의 주인 것이 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "리뷰를 찾지 못함", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{reviewId}/reply")
    ReviewResponse reply(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "리뷰 식별자.", example = "88") @PathVariable Integer reviewId,
            @Valid @RequestBody WriteReviewReplyRequest request) {
        return reviewCommandService.reply(user.userId(), reviewId, request.reply());
    }
}
