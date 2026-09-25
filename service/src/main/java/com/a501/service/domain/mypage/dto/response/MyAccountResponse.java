package com.a501.service.domain.mypage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "마이페이지의 계정 칸.")
public record MyAccountResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "화면에 표시하는 이름.", example = "김미미") String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "카카오 계정 이메일. 동의하지 않았으면 null이다.", example = "mimi@kakao.com") String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "화면에 그대로 거는 프로필 사진 주소. 저장된 값이 아니라 응답을 만들 때마다 새로 만든다. "
                + "직접 올린 사진이 있으면 그 사진의 주소이고, 없으면 카카오 사진 주소다. 둘 다 없으면 null이며 이때 화면은 "
                + "이름 첫 글자를 동그라미에 넣어 보여준다.\n\n직접 올린 사진의 주소는 한동안만 쓸 수 있는 임시 주소다. "
                + "만료되면 다시 받아야 하므로 저장해 두고 오래 쓰지 않는다.",
                example = "https://cdn.komit.example.com/profiles/512.png") String profileImageUrl) {
}
