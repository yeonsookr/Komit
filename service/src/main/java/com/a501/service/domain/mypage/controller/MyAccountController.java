package com.a501.service.domain.mypage.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.mypage.dto.request.UpdateProfileImageRequest;
import com.a501.service.domain.mypage.dto.response.MyAccountResponse;
import com.a501.service.domain.mypage.service.MyAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 마이페이지 계정 칸을 바꾸는 API. 목 명세 {@code 8 계정 · 공통} 태그의 {@code /api/users/me} 아래 엔드포인트입니다. */
@Tag(name = "8 계정 · 공통", description = "마이페이지 계정 칸 바꾸기")
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class MyAccountController {
    private final MyAccountService myAccountService;

    /**
     * 목 명세의 403은 실서버에서 나오지 않습니다. 대상 계정을 요청에 담지 않고, 남이 올린 파일은 400
     * {@code FILE_NOT_ATTACHABLE}로 막습니다. ({@link MyAccountService#changeProfileImage})
     */
    @Operation(
            summary = "프로필 사진 바꾸기",
            description = "올려 둔 사진을 프로필 사진으로 건다. 올리기 전에는 카카오 프로필 사진을 쓰고, "
                    + "한 번 걸면 그 뒤로는 올린 사진을 쓴다.\n\n"
                    + "**파일 자체는 이 API로 보내지 않는다.** 업로드 시작과 완료를 먼저 거쳐 "
                    + "`fileId`를 받고, 그 값만 여기로 보낸다. 이 화면은 저장 버튼이 따로 없어 "
                    + "고르는 즉시 바뀐다.\n\n"
                    + "**바뀌는 것은 토큰의 주인의 사진이다.** 액세스 토큰을 검증해 `sub`를 꺼내 그 "
                    + "계정에 걸며, 대상을 요청에 담지 않는다. `fileId`가 `sub`가 올린 파일인지도 "
                    + "확인하고, 남의 파일이면 걸지 않는다.\n\n"
                    + "- **200**: 바꿨을 때. `profileImageUrl`이 새 사진을 가리킨다\n"
                    + "- **400 / FILE_NOT_ATTACHABLE**: 아직 올리기를 마치지 않았거나 남이 올린 파일일 때다\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **403 / FORBIDDEN**: `sub`가 올린 파일이 아니거나 남의 계정에 걸려 할 때\n"
                    + "- **422 / INVALID_REQUEST**: `fileId`가 없거나 정수가 아닐 때\n"
                    + "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "바꿈"),
            @ApiResponse(responseCode = "400", description = "쓸 수 없는 파일",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "토큰의 주인 것이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/profile-image")
    MyAccountResponse changeProfileImage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateProfileImageRequest request) {
        return myAccountService.changeProfileImage(user.userId(), request.fileId());
    }
}
