package com.a501.service.domain.consulting.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 세션 녹음 한 건.
 *
 * <p>참가자마다 자기 마이크만 녹음해 각자 올립니다. 두 사람 소리를 한 파일로 합치지 않습니다 —
 * 파일이 곧 화자라 나중에 누가 말했는지 가리는 데 손이 들지 않습니다. (R-CON-14)
 *
 * @param recordingId 녹음 식별자. 참가 기록 하나에 녹음 하나라 그 식별자를 그대로 쓴다
 * @param speaker 올린 사람의 이 계약에서의 역할
 * @param fileId 올린 파일
 * @param uploadedAt 올린 시각
 */
@Schema(description = "세션 녹음 한 건")
public record SessionRecordingResponse(
        @Schema(description = "녹음 식별자.", example = "71") Integer recordingId,
        @Schema(description = "이 녹음을 올린 사람의 이 계약에서의 역할. 인증 정보로 정한다.", example = "CLIENT")
                String speaker,
        @Schema(description = "올린 파일 식별자.", example = "612") Long fileId,
        @Schema(description = "올린 시각.") OffsetDateTime uploadedAt) {}
