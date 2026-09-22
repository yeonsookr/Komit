package com.a501.service.domain.diagnosis.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.diagnosis.dto.request.StartDiagnosisRequest;
import com.a501.service.domain.diagnosis.dto.response.DiagnosisResponse;
import com.a501.service.domain.diagnosis.service.DiagnosisService;
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

@Tag(name = "2 사전점검", description = "프로젝트를 검사해 출시 준비 상태를 알아보고 요청서까지 만드는 구간.")
@RestController
@RequestMapping("/api/diagnoses")
@RequiredArgsConstructor
public class DiagnosisController {
    private final DiagnosisService diagnosisService;

    @Operation(
            summary = "진단 시작",
            description = "**화면 — `2_설문1` › 다음 버튼**\n\n"
                    + "사전점검을 시작한다. 이 요청으로 진단 회차가 열리고 설문지 한 벌이 만들어진다.\n\n"
                    + "`projectId`를 주면 그 프로젝트를 다시 진단하고 회차가 1 오른다. 주지 않으면 프로젝트를 새로 만들고 1회차로 시작한다.\n\n"
                    + "- **201**: 진단이 열렸을 때. 검사 전이라 `readinessScore`와 `scannedAt`은 null이다\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **404 / DIAGNOSIS_NOT_FOUND**: `projectId`가 없는 프로젝트이거나 내 프로젝트가 아닐 때\n"
                    + "- **422 / INVALID_REQUEST**: `projectId`도 `name`도 없을 때. 새 프로젝트는 이름이 있어야 한다\n"
                    + "- **500 / INTERNAL_ERROR**: 진단을 열지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "진단 시작"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "프로젝트 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    DiagnosisResponse startDiagnosis(
            @Valid @RequestBody StartDiagnosisRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return diagnosisService.start(request, user.userId());
    }

    @Operation(
            summary = "진단 요약 조회",
            description = "**화면 — `2_진단서` › 머리 · `2_요구사항선택` › 머리**\n\n"
                    + "진단서 위쪽의 준비도 점수와 회차, 요구사항 선택 화면 위쪽의 프로젝트 이름·기술·결함 수를 한 번에 채운다.\n\n"
                    + "`scannedAt`과 `readinessScore`가 둘 다 null이면 아직 검사 결과를 올리기 전이다.\n\n"
                    + "- **200**: 진단이 있고 내 것일 때. 검사 전이면 점수와 시각이 null이고 결함 수는 0이다\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **404 / DIAGNOSIS_NOT_FOUND**: 없는 진단이거나 남의 진단일 때. 존재를 드러내지 않기 위해 둘이 같다\n"
                    + "- **422 / INVALID_REQUEST**: `diagnosisId`가 정수가 아닐 때\n"
                    + "- **500 / INTERNAL_ERROR**: 요약을 만들지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "진단 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{diagnosisId}")
    DiagnosisResponse getDiagnosis(
            @Parameter(description = "진단 식별자.", example = "31") @PathVariable Integer diagnosisId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return diagnosisService.getDiagnosis(diagnosisId, user.userId());
    }
}
