package com.a501.service.domain.diagnosis.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "끝내지 않은 설문 목록.")
public record UnfinishedSurveyListResponse(
        @Schema(description = "끝내지 않은 설문. 없으면 빈 배열이다.")
        List<UnfinishedSurveyResponse> content) {

    @Schema(description = "설문을 끝내지 않은 진단 하나. `2_설문1` 아래 「진행 중인 설문」에 쓴다.")
    public record UnfinishedSurveyResponse(
            @Schema(description = "진단 식별자.", example = "31") Integer diagnosisId,
            @Schema(description = "프로젝트 이름. 목록에서 이것으로 알아본다.", example = "미미헤어샵 예약")
            String projectName,
            @Schema(description = "마지막으로 저장한 시각. 목록은 이 값의 내림차순으로 온다.")
            OffsetDateTime updatedAt) {}
}
