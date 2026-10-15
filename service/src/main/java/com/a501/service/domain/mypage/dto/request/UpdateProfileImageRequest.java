package com.a501.service.domain.mypage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 프로필 사진 바꾸기. 목 명세 {@code UpdateProfileImageRequest}와 같습니다.
 *
 * <p><b>누구의 사진인지는 담지 않습니다.</b> 토큰의 주인으로 정합니다.
 *
 * @param fileId 올리기를 마친 내 파일
 */
@Schema(description = "프로필 사진 바꾸기 요청. 파일이 아니라 올려 둔 파일의 식별자를 보낸다.")
public record UpdateProfileImageRequest(
        @Schema(
                        description = "프로필 사진으로 걸 파일 식별자. 올리기 완료까지 마친 내 파일이어야 한다.",
                        example = "512",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                Integer fileId) {}
