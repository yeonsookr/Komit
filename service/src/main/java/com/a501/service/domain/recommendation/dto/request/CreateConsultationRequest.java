package com.a501.service.domain.recommendation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "상담 요청. 고른 개발자들에게 같은 요청서를 보낸다.")
public record CreateConsultationRequest(
        @Schema(description = "상담을 요청할 추천 항목. 한 명 이상 골라야 하며, 모두 이 회차의 항목이어야 한다.")
        @NotEmpty List<Integer> recommendationItemIds) {}
