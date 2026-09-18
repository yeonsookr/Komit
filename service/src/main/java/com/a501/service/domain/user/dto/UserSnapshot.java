package com.a501.service.domain.user.dto;

public record UserSnapshot(
        Integer userId,
        String name,
        Integer profileImageFileId,
        boolean hasDevProfile) {
}
