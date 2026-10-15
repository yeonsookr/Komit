package com.a501.service.domain.user.dto;

/** 마이페이지 계정 칸을 만드는 데 필요한 사용자 정보. */
public record MyPageUserView(
        String name,
        String email,
        Integer profileImageFileId,
        String kakaoProfileImageUrl) {
}
