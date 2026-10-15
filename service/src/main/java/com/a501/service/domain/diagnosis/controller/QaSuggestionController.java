package com.a501.service.domain.diagnosis.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.common.logging.SlowCallThreshold;
import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionListResponse;
import com.a501.service.domain.diagnosis.service.QaSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 확인 문장 후보를 만든다.
 *
 * <p><b>{@code IssueController} 와 나눠 둔다.</b> 저장된 요구사항을 읽고 고치는 것과 달리 이쪽은
 * 바깥 AI 서비스를 부르고 아무것도 저장하지 않아 실패하는 방식이 다르다. 같은 리소스를 두
 * 컨트롤러가 나눠 맡는 것은 {@code RoomController} 와 {@code ChatController} 의 선례를 따른다.
 */
@Tag(name = "2 사전점검", description = "프로젝트를 검사해 출시 준비 상태를 알아보고 요청서까지 만드는 구간.")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class QaSuggestionController {
    private final QaSuggestionService qaSuggestionService;

    @Operation(
            summary = "확인 문장 후보 생성",
            description = "**화면 — `2_요구사항선택` › 확인 문장 추천**\n\n"
                    + "요구사항 한 건을 AI에 넘겨 의뢰인이 직접 실행하고 통과 여부를 판단할 수 있는 확인 문장 후보 세 건을 만든다.\n\n"
                    + "**저장하지 않는다.** 후보를 보여줄 뿐이며 사용자가 고르고 고친 뒤 요청서 저장이 확정한다. "
                    + "아직 저장 전이라 `qaCriterionId`와 상태를 주지 않는다.\n\n"
                    + "요청 본문이 없다. 저장된 요구사항과 다른 입력이 섞이지 않도록 식별자만 받는다. "
                    + "같은 요구사항으로 다시 불러도 앞의 답과 다를 수 있다.\n\n"
                    + "- **200**: 후보를 만들었을 때. `content`는 언제나 세 건이다\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **404 / ISSUE_NOT_FOUND**: 없는 요구사항이거나 남의 것일 때\n"
                    + "- **422 / INVALID_REQUEST**: 요구사항 식별자 형식이 올바르지 않을 때\n"
                    + "- **502 / AI_RESPONSE_INVALID**: AI가 계약에 맞는 후보를 만들지 못했을 때\n"
                    + "- **503 / AI_SERVICE_UNAVAILABLE**: 확인 문장 생성 서비스에 연결할 수 없을 때\n"
                    + "- **500 / INTERNAL_ERROR**: 그 밖의 서버 오류",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확인 문장 후보 세 건"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "요구사항 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "AI가 계약에 맞는 후보를 만들지 못함", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "확인 문장 생성 서비스에 연결할 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    // 바깥 모델 왕복을 기다리므로 1초를 넘는 것이 정상이다. 공용 기준(1s)을 그대로 두면 성공할
    // 때마다 WARN 이 쌓여 진짜 이상 신호가 가려진다. 읽기 한도(`app.ai.read-timeout`, 기본 25s)보다
    // 조금 크게 잡아, 한도 안에서 끝난 호출은 경고로 보지 않는다.
    @SlowCallThreshold("30s")
    @PostMapping("/issues/{issueId}/qa-suggestions")
    QaSuggestionListResponse suggestQaCriteria(
            @Parameter(description = "요구사항 식별자.", example = "312") @PathVariable Integer issueId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return qaSuggestionService.suggest(issueId, user.userId());
    }
}
