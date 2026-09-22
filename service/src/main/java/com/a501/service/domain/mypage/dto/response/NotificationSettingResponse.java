package com.a501.service.domain.mypage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 수신 설정.")
public record NotificationSettingResponse(
        @Schema(description = "카카오 알림톡을 받을지.", example = "true") boolean notificationEnabled) {
}
