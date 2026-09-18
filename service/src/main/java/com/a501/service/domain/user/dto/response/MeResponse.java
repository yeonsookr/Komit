package com.a501.service.domain.user.dto.response;

public record MeResponse(
        Integer userId,
        String name,
        Integer profileImageFileId,
        boolean hasDevProfile,
        Integer developerId,
        int unreadNotificationCount) {
}
