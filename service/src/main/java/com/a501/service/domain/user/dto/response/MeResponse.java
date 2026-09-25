package com.a501.service.domain.user.dto.response;

/**
 * 모든 화면의 헤더를 채우는 내 정보. 목 명세 {@code MeResponse}와 필드 순서까지 같습니다.
 *
 * @param profileImageUrl 직접 올린 사진, 카카오 사진 순서로 고른 주소. 둘 다 없으면 null. 올린 사진은 임시 주소입니다. (R-FILE-03)
 */
public record MeResponse(
        Integer userId,
        String name,
        Integer profileImageFileId,
        String profileImageUrl,
        boolean hasDevProfile,
        Integer developerId,
        int unreadNotificationCount) {
}
