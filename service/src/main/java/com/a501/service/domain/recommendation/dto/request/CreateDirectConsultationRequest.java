package com.a501.service.domain.recommendation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "직접 찾은 개발자에게 보내는 상담 요청.")
public record CreateDirectConsultationRequest(
        @Schema(description = "상담을 요청할 개발자 식별자. 한 명 이상 골라야 한다.", example = "[5]")
        @NotEmpty List<Integer> developerIds) {}
