package com.a501.service.domain.developer.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.developer.dto.request.WriteDeveloperProfileRequest;
import com.a501.service.domain.developer.dto.response.MyDeveloperProfileResponse;
import com.a501.service.domain.developer.service.DeveloperProfileCommandService;
import com.a501.service.domain.developer.service.DeveloperProfileQueryService;
import com.a501.service.domain.developer.service.DeveloperProfileSaveResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 개발자 프로필. 목 명세 {@code 8 계정 · 공통} 태그의 {@code /api/users/me/developer-profile} 두 엔드포인트입니다. */
@Tag(name = "8 계정 · 공통", description = "내 개발자 프로필 조회·저장")
@RestController
@RequestMapping("/api/users/me/developer-profile")
@RequiredArgsConstructor
public class MyDeveloperProfileController {
    private final DeveloperProfileQueryService queryService;
    private final DeveloperProfileCommandService commandService;

    @Operation(
            summary = "내 개발자 프로필 조회",
            description = "편집 화면이 쓰는 값을 한 번에 내려준다. 남에게 보이는 개발자 상세에 가격표 전부와 받은 리뷰를 더한 모양이다.\n\n"
                    + "- **200**: 항상\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **403 / NO_DEVELOPER_PROFILE**: 개발자 프로필이 없을 때\n"
                    + "- **500 / INTERNAL_ERROR**: 프로필을 만들지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "개발자 프로필이 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    MyDeveloperProfileResponse getMine(@AuthenticationPrincipal AuthenticatedUser user) {
        return queryService.getMine(user.userId());
    }

    @Operation(
            summary = "내 개발자 프로필 저장",
            description = "소개, 기술, 경력, 작업물, 가격표를 통째로 받아 보낸 내용으로 맞춘다. 목록에서 빠진 줄은 지운 것으로 본다. "
                    + "프로필이 없으면 만들며, 그때 계정에 개발자 역할이 더해지고 판매자 약관 동의로 갈음한다.\n\n"
                    + "- **200**: 이미 있던 프로필을 고쳤을 때\n"
                    + "- **201**: 프로필이 없어 새로 만들었을 때\n"
                    + "- **400 / FILE_NOT_ATTACHABLE**: 썸네일·증빙이 올리기를 마치지 않았거나 남이 올린 파일일 때\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **403 / FORBIDDEN**: 요청의 `careerId`·`portfolioItemId`·`priceId`가 내 프로필의 줄이 아닐 때\n"
                    + "- **404 / CODE_NOT_FOUND**: 없는 분야·기술 코드일 때\n"
                    + "- **422 / INVALID_REQUEST**: `intro`가 비었거나 길이·형식 제약을 어겼거나, 경력의 종료일이 시작일보다 빠르거나, 가격이 0 이하일 때\n"
                    + "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "201", description = "프로필이 없어 새로 만들었음"),
            @ApiResponse(responseCode = "400", description = "붙일 수 없는 파일", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "내 것이 아닌 줄", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 코드", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping
    ResponseEntity<MyDeveloperProfileResponse> save(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody WriteDeveloperProfileRequest request) {
        DeveloperProfileSaveResult result = commandService.save(user.userId(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.profile());
    }
}
