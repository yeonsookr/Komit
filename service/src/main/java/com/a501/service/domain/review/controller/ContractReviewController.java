package com.a501.service.domain.review.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.review.dto.request.WriteReviewRequest;
import com.a501.service.domain.review.dto.response.ReviewResponse;
import com.a501.service.domain.review.service.ReviewWriteService;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 리뷰 작성. 목 명세 {@code 5 개발} 태그의 {@code POST /api/contracts/{contractId}/review}입니다. */
@Tag(name = "5 개발", description = "끝난 거래에 리뷰를 남긴다")
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractReviewController {
    private final ReviewWriteService reviewWriteService;

    @Operation(
            summary = "리뷰 작성",
            description = "끝난 거래에 의뢰인이 리뷰를 남긴다. 개발력과 설명력을 따로 매기고 본문은 비워도 된다. "
                    + "남기면 개발자의 두 평점과 리뷰 수에 곧바로 더해진다. (R-REV-01, R-REV-04)\n\n"
                    + "**컨설팅은 양쪽이 들어와 시작한 세션이 끝나면 쓸 수 있다.** 대금 지급을 기다리지 않는다. "
                    + "개발은 개발 계약이 끝나야 한다. 계약 하나에 리뷰는 하나다. (R-REV-02)\n\n"
                    + "- **201**: 남겼을 때\n"
                    + "- **400 / CONTRACT_NOT_COMPLETED**: 취소된 계약이거나 거래를 아직 마치지 않았을 때\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **403 / FORBIDDEN**: 이 계약의 의뢰인이 아닐 때\n"
                    + "- **404 / CONTRACT_NOT_FOUND**: 없는 계약이거나 요청자가 그 계약의 당사자가 아닐 때\n"
                    + "- **409 / REVIEW_ALREADY_WRITTEN**: 이미 리뷰를 남겼을 때\n"
                    + "- **422 / INVALID_REQUEST**: 점수가 1~5 밖이거나 본문이 1000자를 넘을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "남겼음"),
            @ApiResponse(responseCode = "400", description = "거래를 마치지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "의뢰인이 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "계약을 찾지 못함", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 리뷰를 남김", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{contractId}/review")
    ReviewResponse write(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "계약 식별자.", example = "130") @PathVariable Integer contractId,
            @Valid @RequestBody WriteReviewRequest request) {
        return reviewWriteService.write(user.userId(), contractId, request);
    }
}
