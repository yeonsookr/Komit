package com.a501.service.domain.user.dto;

/**
 * 프로필 화면이 쓰는 계정 표시 정보.
 *
 * @param userId              사용자 식별자
 * @param name                표시 이름
 * @param profileImageFileId  직접 올린 프로필 사진 파일 식별자. 없으면 null
 * @param kakaoProfileImageUrl 카카오 프로필 사진 주소. 없으면 null
 */
public record UserProfileView(
        Integer userId,
        String name,
        Integer profileImageFileId,
        String kakaoProfileImageUrl) {
}
