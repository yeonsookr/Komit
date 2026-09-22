package com.a501.service.domain.mypage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "마이페이지의 개발자 프로필 칸.")
public record MyDeveloperResponse(
        @Schema(description = "개발자 식별자.", example = "3") Integer developerId,
        @Schema(description = "받은 리뷰 수.", example = "12") int reviewCount,
        @Schema(description = "아직 하지 않은 컨설팅 세션 수.", example = "1") int upcomingSessionCount) {
}
