package com.a501.service.domain.consulting.dto.response;

import com.a501.service.domain.consulting.dto.Speaker;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 노트북이 받아 가는 전사 할 일. (`job-contract.md` 11.3)
 *
 * @param sessionId 전사할 세션
 * @param tracks 참가자별 녹음. 한 건이 곧 한 사람이다 (R-CON-14)
 */
@Schema(description = "전사 할 일")
public record TranscriptionJobResponse(Integer sessionId, List<Track> tracks) {

    /**
     * 녹음 한 건.
     *
     * @param speaker 누구의 녹음인가
     * @param audioUrl 저장소 읽기 주소. 짧게 만료된다 (R-FILE-03)
     */
    public record Track(Speaker speaker, String audioUrl) {}
}
