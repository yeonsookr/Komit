package com.a501.service.domain.mypage.dto.response;

import com.a501.service.domain.account.dto.response.AccountResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "마이페이지 한 판.")
public record MyPageResponse(
        @Schema(description = "계정 칸.") MyAccountResponse account,
        @Schema(description = "개발자가 대금을 받는 계좌. 등록하지 않았으면 null이다.") AccountResponse settlementAccount,
        @Schema(description = "내가 등록한 프로젝트. 없으면 빈 배열이다.") List<MyProjectResponse> projects,
        @Schema(description = "개발자 프로필 칸. 개발자 프로필이 없으면 null이다.") MyDeveloperResponse developer,
        @Schema(description = "알림 설정.") NotificationSettingResponse notificationSetting) {
}
