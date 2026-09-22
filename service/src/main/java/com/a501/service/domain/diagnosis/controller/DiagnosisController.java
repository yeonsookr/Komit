package com.a501.service.domain.diagnosis.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.diagnosis.dto.request.CreateIssueRequest;
import com.a501.service.domain.diagnosis.dto.request.SaveSurveyAnswersRequest;
import com.a501.service.domain.diagnosis.dto.request.WriteRequirementsRequest;
import com.a501.service.domain.diagnosis.dto.request.StartDiagnosisRequest;
import com.a501.service.domain.diagnosis.dto.response.DiagnosisResponse;
import com.a501.service.domain.diagnosis.IssueStatus;
import com.a501.service.domain.diagnosis.dto.response.InspectionResponse;
import com.a501.service.domain.diagnosis.dto.response.IssueListResponse;
import com.a501.service.domain.diagnosis.dto.response.IssueResponse;
import com.a501.service.domain.diagnosis.dto.response.SurveyResponse;
import com.a501.service.domain.diagnosis.dto.response.UnfinishedSurveyListResponse;
import com.a501.service.domain.diagnosis.service.DiagnosisService;
import com.a501.service.domain.diagnosis.service.ScanFileWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "2 사전점검", description = "프로젝트를 검사해 출시 준비 상태를 알아보고 요청서까지 만드는 구간.")
@RestController
@RequestMapping("/api/diagnoses")
@RequiredArgsConstructor
public class DiagnosisController {
    /**
     * 검사 파일은 사람이 읽는 마크다운이다. 매핑의 produces 로 걸면 협상 단계에서 걸려 컨트롤러까지 오지 못한다. 한글이 담기므로 문자 집합을 함께 밝힌다.
     */
    private static final MediaType SCAN_FILE_MEDIA_TYPE =
            MediaType.parseMediaType("text/markdown; charset=UTF-8");

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
            summary = "끝내지 않은 설문 목록",
            description = "**화면 — `2_설문1` › 진행 중인 설문**\n\n"
                    + "설문을 끝내지 않고 나간 진단을 돌려준다. 다시 들어온 사람이 처음부터 다시 하지 않고 이어서 하게 하려는 것이다.\n\n"
                    + "**끝냈는지는 설문의 완료 여부로 본다.** 설문4까지 마치고 완료를 부르면 그 설문은 여기서 빠진다. 검사를 돌렸는지와는 무관하다 — 설문3까지 하고 나간 사람도 남는다.\n\n"
                    + "내 프로젝트의 것만 내려간다. 마지막으로 저장한 시각의 내림차순이다.\n\n"
                    + "- **200**: 목록을 내줬을 때. 없으면 `content`가 빈 배열이다\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **500 / INTERNAL_ERROR**: 목록을 읽지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    UnfinishedSurveyListResponse getUnfinishedSurveys(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return diagnosisService.getUnfinishedSurveys(user.userId());
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

    @Operation(
            summary = "결함 목록 조회",
            description = "**화면 — `2_진단서` › 문제점 목록 · `2_요구사항선택` › 요구사항 목록**\n\n"
                    + "이 진단 회차에서 나온 결함을 내려준다. 진단서는 사용자가 적은 증상과 관련된 것을 위에 따로 보여주므로 `isInput`으로 가른다. 요구사항 선택 화면은 해결한 것을 아래로 내리므로 `status`로 가른다.\n\n"
                    + "**요청서도 이 목록으로 그린다.** 요청서는 따로 저장되는 문서가 아니라 이 요구사항들을 모아 부르는 이름이다.\n\n"
                    + "- **200**: 진단이 있을 때. 검사 전이면 `content`가 빈 배열이다\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **404 / DIAGNOSIS_NOT_FOUND**: 없는 진단이거나 남의 진단일 때\n"
                    + "- **422 / INVALID_REQUEST**: `status`나 페이지 값이 허용된 범위를 벗어났을 때\n"
                    + "- **500 / INTERNAL_ERROR**: 목록을 만들지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "진단 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{diagnosisId}/issues")
    IssueListResponse getIssues(
            @Parameter(description = "진단 식별자.", example = "31") @PathVariable Integer diagnosisId,
            @Parameter(description = "처리 상태로 거른다. 비우면 전부 내려간다.", example = "UNRESOLVED")
            @RequestParam(required = false) IssueStatus status,
            @Parameter(description = "사용자가 적은 증상과 관련된 것만 거른다. 비우면 전부 내려간다.")
            @RequestParam(required = false) Boolean isInput,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return diagnosisService.getIssues(
                diagnosisId, user.userId(), status == null ? null : status.name(), isInput, page, size);
    }

    @Operation(
            summary = "요구사항 직접 추가",
            description = "**화면 — `2_요구사항선택` › + 요구사항 추가**\n\n"
                    + "검사가 찾지 못했지만 고치고 싶은 것을 사용자가 직접 적어 넣는다. 검사에서 나온 결함과 같은 목록에 미해결로 들어가고, 요청서에도 함께 담긴다.\n\n"
                    + "- **201**: 추가했을 때. 위치는 코드에서 찾은 값이 아니므로 null이다\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **404 / DIAGNOSIS_NOT_FOUND**: 없는 진단이거나 남의 진단일 때\n"
                    + "- **409 / INSPECTION_NOT_DONE**: 아직 검사 결과를 올리지 않아 요구사항을 붙일 검사가 없을 때\n"
                    + "- **422 / INVALID_REQUEST**: `title`이 비었거나 `severity`가 허용된 값이 아닐 때\n"
                    + "- **500 / INTERNAL_ERROR**: 추가하지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "요구사항 추가"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "진단 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "검사 전", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{diagnosisId}/issues")
    IssueResponse addIssue(
            @Parameter(description = "진단 식별자.", example = "31") @PathVariable Integer diagnosisId,
            @Valid @RequestBody CreateIssueRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return diagnosisService.addIssue(diagnosisId, user.userId(), request);
    }

    @Operation(
            summary = "요청서 저장",
            description = "**화면 — `2_의뢰인_요청서수정` › 저장하기**\n\n"
                    + "요청서를 통째로 받아 **보낸 내용으로 맞춘다.** 화면의 저장 버튼 하나가 이 요청 하나를 보낸다.\n\n"
                    + "보낸 목록에 없는 기존 요구사항은 요청서에서 뺀 것으로 보고, 요구사항 안에서 보낸 목록에 없는 확인 문장도 지운 것으로 본다. 새로 적는 줄은 식별자를 null로 보내면 서버가 만들어 응답에 담아 준다.\n\n"
                    + "**보내는 것이 아니라 저장하는 것이다.** 요청서는 방에 걸려 있어 개발자가 언제든 같은 것을 본다.\n\n"
                    + "**이미 계약에 들어간 요구사항은 뺄 수 없다.** 계약서에 적힌 작업이 사라지면 안 되기 때문이다.\n\n"
                    + "- **200**: 저장했을 때. 저장된 뒤의 요구사항 전부를 준다\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **404 / DIAGNOSIS_NOT_FOUND**: 없는 진단이거나 남의 진단일 때\n"
                    + "- **409 / ISSUE_STATUS_CONFLICT**: 계약에 포함된 요구사항을 빼려 할 때\n"
                    + "- **409 / INSPECTION_NOT_DONE**: 아직 검사 결과를 올리지 않았을 때\n"
                    + "- **422 / INVALID_REQUEST**: 요구사항 문장이 비었거나 `severity`가 허용된 값이 아닐 때\n"
                    + "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "진단 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "뺄 수 없는 요구사항", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{diagnosisId}/requirements")
    IssueListResponse writeRequirements(
            @Parameter(description = "진단 식별자.", example = "31") @PathVariable Integer diagnosisId,
            @Valid @RequestBody WriteRequirementsRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return diagnosisService.writeRequirements(diagnosisId, user.userId(), request.requirements());
    }

    @Operation(
            summary = "검사 파일 내려받기",
            description = "**화면 — `2_설문3` › ① 검사 파일 받기 · 내려받기**\n\n"
                    + "의뢰인이 자기 AI 도구에서 돌릴 검사 파일을 내려받는다. 이 파일을 도구에 읽히면 결과 파일이 만들어지고, 그 결과 파일만 다시 올린다. 소스 코드는 올리지 않는다.\n\n"
                    + "파일에는 이 진단의 번호가 들어 있다. 검사 도구가 그 번호를 결과 파일에 옮겨 적고, 올릴 때 서버가 대조한다.\n\n"
                    + "- **200**: 내려받았을 때. 본문이 파일 내용이고 파일 이름은 `Content-Disposition`에 담긴다\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **404 / DIAGNOSIS_NOT_FOUND**: 없는 진단이거나 남의 진단일 때\n"
                    + "- **500 / INTERNAL_ERROR**: 내려주지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검사 파일. 본문이 파일 내용이다.",
                    content = @Content(mediaType = "text/markdown",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "진단 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{diagnosisId}/scan-file")
    ResponseEntity<byte[]> downloadScanFile(
            @Parameter(description = "진단 식별자.", example = "31") @PathVariable Integer diagnosisId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        byte[] body = diagnosisService.getScanFile(diagnosisId, user.userId());
        return ResponseEntity.ok()
                .contentType(SCAN_FILE_MEDIA_TYPE)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(ScanFileWriter.FILE_NAME).build().toString())
                .body(body);
    }

    @Operation(
            summary = "검사 결과 파일 올리기",
            description = "**화면 — `2_설문3` › ③ 만들어진 결과 파일 올리기**\n\n"
                    + "사용자가 자기 AI 도구로 돌린 검사 결과 파일을 올린다. 코드 자체는 올라오지 않고 결과 파일만 받는다.\n\n"
                    + "올린 결과 파일에는 발견한 문제뿐 아니라, 코드만으로는 판단할 수 없어 사용자에게 물어야 할 질문도 함께 들어 있다. 이 요청으로 그 질문들이 설문4 문항이 된다.\n\n"
                    + "- **201**: 파일을 받아 검사가 열렸을 때. 이어서 설문 조회로 설문4 문항을 받는다\n"
                    + "- **400 / SCAN_FILE_INVALID**: 받은 형식이 아닌 파일을 올렸을 때\n"
                    + "- **400 / DIAGNOSIS_MISMATCH**: 파일에 진단 번호가 적혀 있고 그것이 `diagnosisId`와 다를 때. 번호가 없는 파일은 받는다\n"
                    + "- **400 / SCAN_VERSION_OUTDATED**: 파일의 검사 지시문 판이 지금 지원하는 판과 다를 때\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **404 / DIAGNOSIS_NOT_FOUND**: 없는 진단이거나 남의 진단일 때\n"
                    + "- **500 / INTERNAL_ERROR**: 검사를 시작하지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "검사 열림"),
            @ApiResponse(responseCode = "400", description = "결과 파일을 받지 못함", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "진단 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(value = "/{diagnosisId}/inspections", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    InspectionResponse uploadScanResult(
            @Parameter(description = "진단 식별자.", example = "31") @PathVariable Integer diagnosisId,
            @Parameter(description = "AI 도구가 만든 검사 결과 파일.") @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return diagnosisService.uploadScanResult(diagnosisId, user.userId(), file);
    }

    @Operation(
            summary = "설문 조회",
            description = "**화면 — `2_설문4` › 문항과 보기. 설문1~3은 이어하기에만 쓴다**\n\n"
                    + "설문 문항과 보기를 순서대로 내려준다. 이미 답한 보기는 `isSelected`가 참이라 뒤로 갔다 와도 고른 상태가 그대로 보인다.\n\n"
                    + "설문1~3은 모든 프로젝트에 같은 고정 문항이라 화면이 문구를 갖고 있다. 설문4는 올린 검사 결과에서 나오므로 반드시 이 API로 받아 그린다.\n\n"
                    + "- **200**: 진단이 있고 내 것일 때\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **404 / DIAGNOSIS_NOT_FOUND**: 없는 진단이거나 남의 진단일 때\n"
                    + "- **500 / INTERNAL_ERROR**: 설문을 읽지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "진단 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{diagnosisId}/survey")
    SurveyResponse getSurvey(
            @Parameter(description = "진단 식별자.", example = "31") @PathVariable Integer diagnosisId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return diagnosisService.getSurvey(diagnosisId, user.userId());
    }

    @Operation(
            summary = "설문 답 저장",
            description = "**화면 — `2_설문1` · `2_설문2` · `2_설문4` › 다음 버튼**\n\n"
                    + "한 단계에서 고른 답을 저장한다. 보낸 문항만 바뀌고 나머지는 그대로 남는다. 같은 단계를 다시 보내도 결과가 같다.\n\n"
                    + "저장 뒤의 설문지 전체를 돌려주므로 화면은 응답으로 상태를 맞추면 된다.\n\n"
                    + "- **200**: 저장했을 때\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **404 / DIAGNOSIS_NOT_FOUND**: 없는 진단이거나 남의 진단일 때\n"
                    + "- **422 / INVALID_REQUEST**: 단수 문항에 보기를 둘 이상 보냈거나, 주관식에 보기 식별자를 보냈거나, 이 설문의 문항·보기가 아닐 때\n"
                    + "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "진단 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{diagnosisId}/survey/answers")
    SurveyResponse saveSurveyAnswers(
            @Parameter(description = "진단 식별자.", example = "31") @PathVariable Integer diagnosisId,
            @Valid @RequestBody SaveSurveyAnswersRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return diagnosisService.saveSurveyAnswers(diagnosisId, user.userId(), request.answers());
    }

    @Operation(
            summary = "설문 완료",
            description = "**화면 — `2_설문4` › 진단서 보기**\n\n"
                    + "설문을 끝냈다고 알린다. 이 호출을 받아야 설문이 완료로 바뀌고 `2_설문1`의 「진행 중인 설문」 목록에서 빠진다.\n\n"
                    + "답 저장이 완료를 겸하지 않는다. 설문4 문항은 검사 결과에서 나오므로, 답이 다 찼는지로 판정하면 설문2까지만 하고도 완료로 찍힌다.\n\n"
                    + "완료 조건은 서버가 확인한다 — 검사가 끝나 설문4 문항이 생겼고 모든 문항에 답이 있어야 한다.\n\n"
                    + "- **200**: 완료로 바뀌었을 때. `isDone`이 참인 설문지를 돌려준다\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **404 / DIAGNOSIS_NOT_FOUND**: 없는 진단이거나 남의 진단일 때\n"
                    + "- **409 / SURVEY_NOT_COMPLETABLE**: 답하지 않은 문항이 남았거나 이미 끝낸 설문일 때\n"
                    + "- **500 / INTERNAL_ERROR**: 완료 처리를 하지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "완료 처리"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "진단 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "아직 끝낼 수 없거나 이미 끝냄", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{diagnosisId}/survey/complete")
    SurveyResponse completeSurvey(
            @Parameter(description = "진단 식별자.", example = "31") @PathVariable Integer diagnosisId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return diagnosisService.completeSurvey(diagnosisId, user.userId());
    }
}
