package com.a501.service.domain.auth.dto.response;

import com.a501.service.domain.user.dto.UserSnapshot;

public record LoginUserResponse(
        Integer userId,
        String name,
        Integer profileImageFileId,
        boolean hasDevProfile) {
    public static LoginUserResponse from(UserSnapshot user) {
        return new LoginUserResponse(
                user.userId(), user.name(), user.profileImageFileId(), user.hasDevProfile());
    }
}
