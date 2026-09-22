package com.a501.service.domain.diagnosis.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.diagnosis.dto.request.UpdateIssueStatusRequest;
import com.a501.service.domain.diagnosis.dto.response.IssueResponse;
import com.a501.service.domain.diagnosis.service.IssueQueryService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "2 사전점검", description = "프로젝트를 검사해 출시 준비 상태를 알아보고 요청서까지 만드는 구간.")
@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {
    private final IssueQueryService issueQueryService;

    @Operation(
            summary = "요구사항 해결 처리 · 되돌리기",
            description = "**화면 — `2_요구사항선택` › 직접 해결했어요 / 되돌리기 · `2_클릭_직접해결했어요` › 해결 처리**\n\n"
                    + "이미 스스로 고친 요구사항을 해결로 내리거나, 잘못 눌렀을 때 미해결로 되돌린다. 해결로 내린 항목은 목록 아래쪽으로 가고 요청서에 담기지 않는다.\n\n"
                    + "같은 상태를 다시 보내도 결과가 같다.\n\n"
                    + "- **200**: 상태를 바꿨을 때\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **404 / ISSUE_NOT_FOUND**: 없는 요구사항이거나 남의 것일 때\n"
                    + "- **409 / ISSUE_STATUS_CONFLICT**: 이미 계약에 들어간 요구사항이라 사전점검에서 상태를 바꿀 수 없을 때\n"
                    + "- **422 / INVALID_REQUEST**: `status`가 RESOLVED도 UNRESOLVED도 아닐 때\n"
                    + "- **500 / INTERNAL_ERROR**: 바꾸지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상태 변경"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "요구사항 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "계약에 포함되어 상태를 바꿀 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{issueId}")
    IssueResponse updateIssueStatus(
            @Parameter(description = "요구사항 식별자.", example = "314") @PathVariable Integer issueId,
            @Valid @RequestBody UpdateIssueStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return issueQueryService.updateStatus(issueId, user.userId(), request.status());
    }
}
