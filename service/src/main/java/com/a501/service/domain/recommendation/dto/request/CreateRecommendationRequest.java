package com.a501.service.domain.recommendation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "추천 회차 생성 요청.")
public record CreateRecommendationRequest(
        @Schema(description = "어느 진단을 근거로 찾을지. 이 API 는 진단 없이 부를 수 없다.", example = "31")
        @NotNull Integer diagnosisId,
        @Schema(description = "요청서에 담을 요구사항 식별자. 한 건 이상 골라야 하며, 모두 이 진단에서 나온 것이어야 한다.")
        @NotEmpty List<Integer> issueIds) {}
