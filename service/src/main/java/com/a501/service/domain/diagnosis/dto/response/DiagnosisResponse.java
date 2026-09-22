package com.a501.service.domain.diagnosis.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "진단 한 건. 진단서와 요구사항 선택 화면의 머리를 함께 채운다.")
public record DiagnosisResponse(
        @Schema(description = "진단 식별자.", example = "31") Integer diagnosisId,
        @Schema(description = "진단 회차. 재진단할 때마다 1씩 오른다.", example = "2") Integer version,
        @Schema(description = "진단 대상 프로젝트.") DiagnosisProjectResponse project,
        @Schema(description = "사용자가 적은 현재 증상. 적지 않았으면 null 이다.") String userInput,
        @Schema(description = "런칭 준비도 점수. 검사가 끝나기 전이면 null 이다.", example = "52")
        Integer readinessScore,
        @Schema(description = "검사를 실행한 시각. 검사 전이면 null 이다.", example = "2026-08-17T00:12:00Z")
        OffsetDateTime scannedAt,
        @Schema(description = "아직 해결되지 않은 결함 수.", example = "9") long unresolvedIssueCount,
        @Schema(description = "이번 회차에서 발견된 결함 수.", example = "11") long totalIssueCount,
        @Schema(description = "지난 회차에 있었는데 이번 회차에서 사라진 결함 수. 1회차면 0이다.", example = "3")
        long resolvedSinceLastVersionCount) {}
