package com.a501.service.domain.recommendation.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.recommendation.dto.request.CreateRecommendationRequest;
import com.a501.service.domain.recommendation.dto.response.RecommendationBatchResponse;
import com.a501.service.domain.recommendation.service.RecommendationQueryService;
import com.a501.service.domain.recommendation.service.RecommendationService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "3 개발자 찾기 · 상담방", description = "개발자를 고르고 무료 상담방에서 이야기한 뒤 갈래를 정한다.")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendationController {
    private final RecommendationService recommendationService;
    private final RecommendationQueryService recommendationQueryService;

    @Operation(
            summary = "개발자 추천 받기",
            description = "**화면 — `2_요구사항선택` › 상담 요청하기. 결과는 `3_의뢰인_개발자추천`에 나온다**\n\n"
                    + "고른 요구사항을 근거로 맞는 개발자를 찾아 추천 회차를 만든다. 추천은 정해진 규칙으로 점수를 계산하며, 서버는 추천 회차의 알고리즘 판과 항목별 점수 근거를 보관한다. (R-MATCH-05)\n\n"
                    + "**직접 찾아 고른 개발자는 이 API를 거치지 않는다.** 그 길은 프로젝트 상담 요청 API가 맡고, 회차 없이 방이 바로 열린다.\n\n"
                    + "같은 프로젝트에서 다시 찾으면 회차 번호가 1 오른다. 이전 회차는 지우지 않고 남긴다.\n\n"
                    + "**아직 열어보지 않은 회차가 있으면 새로 만들지 않는다.** 직전 회차를 한 번도 열지 않았다면 그 회차를 그대로 돌려준다. 새로고침하거나 버튼을 두 번 눌러 빈 회차가 쌓이는 것을 막는다.\n\n"
                    + "**추천을 무엇 때문에 만들었는지는 서버가 정한다.** 첫 회차면 상담 요청, 이미 회차가 있으면 재매칭으로 기록하고 직전 회차를 부모로 잇는다.\n\n"
                    + "- **201**: 추천을 만들었을 때. 맞는 개발자가 없으면 `items`가 빈 배열이다\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **404 / PROJECT_NOT_FOUND**: 없거나 남의 프로젝트일 때\n"
                    + "- **404 / DIAGNOSIS_NOT_FOUND**: 없거나 남의 진단일 때\n"
                    + "- **422 / INVALID_REQUEST**: `diagnosisId`가 없거나, `issueIds`가 비었거나, **이 진단에서 나오지 않은 요구사항이 섞였을 때.** 고르지 않은 것이 요청서에 실리면 계약까지 따라가므로 조용히 버리지 않고 막는다\n"
                    + "- **500 / INTERNAL_ERROR**: 추천을 만들지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "추천 생성"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "프로젝트 또는 진단 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/projects/{projectId}/recommendations")
    RecommendationBatchResponse createRecommendation(
            @Parameter(description = "프로젝트 식별자.", example = "8") @PathVariable Integer projectId,
            @Valid @RequestBody CreateRecommendationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return recommendationService.create(projectId, user.userId(), request);
    }

    @Operation(
            summary = "추천 결과 조회",
            description = "**화면 — `3_의뢰인_개발자추천` › 추천 개발자 목록**\n\n"
                    + "추천 회차의 결과를 순위대로 내려준다. 개발자마다 상담을 요청했는지는 `roomId`로 안다. 비어 있으면 아직 요청하지 않은 개발자다.\n\n"
                    + "**이 조회가 회차를 '열어본 것'으로 표시한다.** 처음 불릴 때 열람 시각을 남기며, 그 표시가 있어야 추천 받기를 다시 불렀을 때 새 회차가 만들어진다. 표시가 없는 동안에는 추천 받기가 같은 회차를 계속 돌려주므로, **이 API를 부르지 않으면 두 번째 회차가 영영 생기지 않는다.**\n\n"
                    + "조회인데 기록을 남기지만 몇 번을 불러도 결과는 같다. 열람 시각은 처음 한 번만 찍히고 그 뒤로는 아무것도 바뀌지 않는다.\n\n"
                    + "- **200**: 추천 회차가 있고 내 것일 때. 맞는 개발자가 없던 회차면 `items`가 빈 배열이다\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **404 / RECOMMENDATION_NOT_FOUND**: 없거나 남의 추천일 때\n"
                    + "- **500 / INTERNAL_ERROR**: 결과를 읽지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추천 결과"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "추천 결과 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/recommendations/{batchId}")
    RecommendationBatchResponse getRecommendation(
            @Parameter(description = "추천 회차 식별자.", example = "61") @PathVariable Integer batchId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return recommendationQueryService.get(batchId, user.userId());
    }
}
