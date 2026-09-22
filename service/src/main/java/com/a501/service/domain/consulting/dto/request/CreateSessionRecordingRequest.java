package com.a501.service.domain.consulting.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 세션 녹음 올리기.
 *
 * <p><b>누가 올렸는지는 담지 않습니다.</b> 인증 정보로 정합니다. 요청에 화자를 실으면 남의
 * 이름으로 올릴 수 있습니다. (R-CON-14)
 *
 * @param fileId 올리기를 마친 파일
 */
@Schema(description = "세션 녹음 올리기 요청")
public record CreateSessionRecordingRequest(
        @Schema(
                        description = "POST /api/uploads 로 올려 받은 파일 식별자. 파일 자체는 이 API 를 지나가지 않는다.",
                        example = "612",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                Long fileId) {}
