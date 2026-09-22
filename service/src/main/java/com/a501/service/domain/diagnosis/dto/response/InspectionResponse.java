package com.a501.service.domain.diagnosis.dto.response;

import com.a501.service.domain.diagnosis.entity.Inspection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "검사 한 건. 검사중 화면이 이 상태를 확인한다.")
public record InspectionResponse(
        @Schema(description = "검사 식별자.", example = "27") Integer inspectionId,
        @Schema(description = "이 검사를 돌린 시점의 진단 회차.", example = "2") Integer diagnosisVersion,
        @Schema(description = "검사 진행. PENDING은 대기, IN_PROGRESS는 검사중, DONE이면 진단서를 볼 수 있다.", example = "IN_PROGRESS")
        String status,
        @Schema(description = "런칭 준비도 점수. 끝나기 전이면 null 이다.") Integer readinessScore,
        @Schema(description = "검사를 실행한 시각. 끝나기 전이면 null 이다.") OffsetDateTime scannedAt) {
    public static InspectionResponse from(Inspection entity) {
        return new InspectionResponse(
                entity.getId(),
                entity.getDiagnosisVersion().intValue(),
                entity.getStatus(),
                entity.getReadinessScore(),
                entity.getScannedAt() == null ? null : entity.getScannedAt().atOffset(ZoneOffset.UTC));
    }
}
