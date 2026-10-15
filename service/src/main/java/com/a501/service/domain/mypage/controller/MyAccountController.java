package com.a501.service.domain.mypage.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.mypage.dto.request.UpdateMyNameRequest;
import com.a501.service.domain.mypage.dto.request.UpdateProfileImageRequest;
import com.a501.service.domain.mypage.dto.response.MyAccountResponse;
import com.a501.service.domain.mypage.dto.response.WithdrawalCheckResponse;
import com.a501.service.domain.mypage.dto.response.WithdrawalResultResponse;
import com.a501.service.domain.mypage.service.MyAccountService;
import com.a501.service.domain.mypage.service.WithdrawalService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 마이페이지 계정 칸을 바꾸는 API와 회원 탈퇴 API. 목 명세 {@code 8 계정 · 공통} 태그의 {@code /api/users/me} 아래 엔드포인트입니다. */
@Tag(name = "8 계정 · 공통", description = "마이페이지 계정 칸 바꾸기와 회원 탈퇴")
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class MyAccountController {
    private final MyAccountService myAccountService;
    private final WithdrawalService withdrawalService;

    @Operation(
            summary = "이름 바꾸기",
            description = "화면에 표시하는 이름을 바꾼다. 카카오 닉네임으로 시작하지만 이후에는 따로 간다 — "
                    + "카카오에서 닉네임을 바꿔도 여기 이름은 그대로다.\n\n"
                    + "이미 남긴 리뷰나 주고받은 메시지에 붙은 이름도 이 이름을 따라 바뀐다.\n\n"
                    + "**바꾸려는 계정이 토큰의 주인인지 검증한다.** 액세스 토큰을 검증해 `sub`를 꺼내고, "
                    + "바꾸려는 계정이 그 `sub`와 같은지 확인한 뒤에 바꾼다. 다르면 403으로 막는다. "
                    + "로그인했다는 것만으로 남의 이름을 바꿀 수 없다.\n\n"
                    + "**403은 나오지 않는다.** 대상을 요청에 담지 않아 남의 것을 가리킬 자리가 없고, "
                    + "토큰을 고쳐 남의 `sub`를 넣으면 서명이 깨져 401이 된다.\n\n"
                    + "- **200**: 바꿨을 때\n"
                    + "- **401 / UNAUTHORIZED**: 토큰이 없거나 만료·위조됐을 때, `sub`의 사용자가 없거나 탈퇴한 계정일 때\n"
                    + "- **422 / INVALID_REQUEST**: `name`이 비었거나 20자를 넘었을 때\n"
                    + "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "바꿈"),
            @ApiResponse(responseCode = "401", description = "인증되지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping
    MyAccountResponse changeName(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateMyNameRequest request) {
        return myAccountService.changeName(user.userId(), request.name());
    }

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

    @Operation(
            summary = "탈퇴 전 확인",
            description = "지금 탈퇴할 수 있는지와, 막고 있는 것이 무엇인지 내려준다. 화면은 `canWithdraw`가 거짓이면 "
                    + "탈퇴 버튼을 잠그고 `blockers`를 줄로 늘어놓는다.\n\n"
                    + "진행 중인 의뢰, 남은 예치금, 아직 받지 못한 소득이 막는다. 예치금과 소득은 Escrow 서버에서 받아와 센다.\n\n"
                    + "**403은 나오지 않는다.** 대상을 요청에 담지 않아 남의 것을 가리킬 자리가 없고, "
                    + "토큰을 고쳐 남의 `sub`를 넣으면 서명이 깨져 401이 된다.\n\n"
                    + "- **200**: 항상\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **500 / INTERNAL_ERROR**: 확인하지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확인함"),
            @ApiResponse(responseCode = "401", description = "인증되지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/withdrawal")
    WithdrawalCheckResponse checkWithdrawal(@AuthenticationPrincipal AuthenticatedUser user) {
        return withdrawalService.check(user.userId());
    }

    @Operation(
            summary = "회원 탈퇴",
            description = "**행을 지우지 않는다.** 사용자 정보는 그대로 두고 삭제 표시만 켠 뒤 삭제 시각을 적는다. "
                    + "그래서 이 API는 `DELETE`가 아니라 `POST`다 — 지우는 요청으로 읽히면 안 되기 때문이다.\n\n"
                    + "표시가 켜지면 그 계정으로는 로그인할 수 없고 카카오 연결도 끊긴다. "
                    + "같은 카카오 계정으로 30일 동안 다시 가입할 수 없다.\n\n"
                    + "**정보를 남기는 이유는 상대가 있기 때문이다.** 계약과 정산 이력, 주고받은 메시지, 남긴 리뷰는 "
                    + "상대 화면에도 있는 기록이라 그대로 두고, 표시 이름만 '탈퇴한 사용자'로 바뀐다. "
                    + "개인정보는 30일 보관 후 지우되 법으로 보관해야 하는 자료는 법정 기간 동안 남는다.\n\n"
                    + "탈퇴 전 확인과 같은 조건을 여기서 다시 본다. 화면이 버튼을 잠갔더라도 서버가 한 번 더 막는다.\n\n"
                    + "**탈퇴하는 계정은 토큰의 주인이다.** 액세스 토큰을 검증해 `sub`를 꺼내 그 계정만 탈퇴시키며, "
                    + "대상을 요청에 담지 않는다. 되돌릴 수 없는 요청이므로 로그인했다는 것만으로 통과시키지 않는다.\n\n"
                    + "**403은 나오지 않는다.** 대상을 요청에 담지 않아 남의 것을 가리킬 자리가 없고, "
                    + "토큰을 고쳐 남의 `sub`를 넣으면 서명이 깨져 401이 된다.\n\n"
                    + "- **200**: 탈퇴했을 때\n"
                    + "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
                    + "- **409 / WITHDRAWAL_BLOCKED**: 진행 중인 의뢰나 남은 돈이 있을 때\n"
                    + "- **500 / INTERNAL_ERROR**: 처리하지 못했을 때",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탈퇴함"),
            @ApiResponse(responseCode = "401", description = "인증되지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "진행 중인 의뢰나 남은 돈이 있음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/withdrawal")
    WithdrawalResultResponse withdraw(@AuthenticationPrincipal AuthenticatedUser user) {
        return withdrawalService.withdraw(user.userId());
    }
}
