package com.a501.service.domain.mypage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 이름 바꾸기. 목 명세 {@code UpdateMyNameRequest}와 같습니다.
 *
 * <p><b>누구의 이름인지는 담지 않습니다.</b> 토큰의 주인으로 정합니다. 값을 다듬지(trim) 않습니다. 목이 공백을 그대로 받기 때문입니다.
 *
 * @param name 새로 쓸 표시 이름. 목: {@code min_length=1}, {@code max_length=20}
 */
@Schema(description = "이름 바꾸기 요청.")
public record UpdateMyNameRequest(
        @Schema(
                        description = "새로 쓸 표시 이름. 20자까지 받는다. 카카오 닉네임과 달라도 된다.",
                        example = "김미미",
                        minLength = 1,
                        maxLength = 20,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @Size(min = 1, message = "{komit.minLength}")
                @Size(max = 20)
                String name) {}
