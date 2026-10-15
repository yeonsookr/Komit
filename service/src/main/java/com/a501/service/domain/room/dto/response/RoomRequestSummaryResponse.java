package com.a501.service.domain.room.dto.response;

import com.a501.service.domain.diagnosis.dto.RequestSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 방 머리에 붙는 요청서 요약.
 *
 * <p><b>요청서는 방이 아니라 프로젝트에 달립니다.</b> 상담·컨설팅·개발 방이 모두 같은 요청서를
 * 보므로, 요청서를 고치면 열려 있는 모든 방의 머리가 함께 바뀝니다.
 *
 * @param diagnosisId 요청서의 근거가 된 진단
 * @param issueCount 요청서에 담긴 요구사항 수
 * @param updatedAt 요청서를 마지막으로 고친 시각
 */
@Schema(description = "방 머리에 붙는 요청서 요약")
public record RoomRequestSummaryResponse(
        @Schema(description = "요청서의 근거가 된 진단 식별자.", example = "31") Integer diagnosisId,
        @Schema(
                        description =
                                "요청서에 담긴 요구사항 수. 이번 회차에서 나온 것 가운데 직접 해결하지 않은 것을"
                                        + " 센다. 계약에 들어간 것은 그대로 남고, 지난 회차의 것은 세지 않는다.",
                        example = "3")
                long issueCount,
        @Schema(description = "요청서를 마지막으로 고친 시각.") OffsetDateTime updatedAt) {

    public static RoomRequestSummaryResponse from(RequestSummary summary) {
        return new RoomRequestSummaryResponse(
                summary.diagnosisId(),
                summary.issueCount(),
                summary.updatedAt().atOffset(ZoneOffset.UTC));
    }
}
