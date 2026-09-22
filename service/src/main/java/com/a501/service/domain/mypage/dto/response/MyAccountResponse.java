package com.a501.service.domain.mypage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "마이페이지의 계정 칸.")
public record MyAccountResponse(
        @Schema(description = "화면에 표시하는 이름.", example = "김미미") String name,
        @Schema(description = "카카오 계정 이메일. 동의하지 않았으면 null이다.", example = "mimi@kakao.com") String email,
        @Schema(description = "화면에 그대로 거는 프로필 사진 주소. 없으면 null이다.") String profileImageUrl) {
}
