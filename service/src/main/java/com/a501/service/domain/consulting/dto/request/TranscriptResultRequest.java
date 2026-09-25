package com.a501.service.domain.consulting.dto.request;

import com.a501.service.domain.consulting.dto.Speaker;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 노트북이 올리는 전사 결과. 받은 모양 그대로 세션에 남긴다. (`job-contract.md` 11.3)
 *
 * <p><b>발화가 하나도 없어도 받는다.</b> 두 사람 모두 말하지 않은 세션이 있을 수 있다.
 *
 * @param utterances 두 녹음을 시각순으로 합친 발화
 */
@Schema(description = "전사 결과")
public record TranscriptResultRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull List<@Valid Utterance> utterances) {

    /**
     * 발화 한 줄.
     *
     * @param utteranceIndex 이 목록 안의 순번. 요약의 근거가 이 번호를 가리킨다
     * @param speaker 누가 말했나
     * @param startMs 세션 녹음이 시작된 뒤 몇 ms 에 말이 시작됐나
     * @param endMs 끝났나
     * @param rawText 받아 적은 그대로
     */
    public record Utterance(
            @NotNull @Min(0) Integer utteranceIndex,
            @NotNull Speaker speaker,
            @NotNull @Min(0) Integer startMs,
            @NotNull @Min(0) Integer endMs,
            @NotNull String rawText) {}
}
