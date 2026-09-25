package com.a501.service.domain.consulting.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 노트북이 전사하지 못했을 때 올리는 보고. (`job-contract.md` 11.3)
 *
 * @param code 다시 받아 가면 되는지, 사람이 봐야 하는지
 * @param message 노트북이 적은 사유. 로그에만 남고 사용자에게 보이지 않는다
 */
@Schema(description = "전사 실패 보고")
public record TranscriptFailureRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Code code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull String message) {

    public enum Code {
        /** 노트북 쪽 장비·연결 문제. 다음에 다시 받아 간다 */
        TRANSCRIBER_UNAVAILABLE,
        /** 녹음을 쓸 수 없다. 다시 걸어도 소용없어 사람이 본다 */
        AUDIO_UNUSABLE
    }
}
