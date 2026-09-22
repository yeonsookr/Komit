package com.a501.service.domain.mypage.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "마이페이지에 한 줄로 나가는, 내가 등록한 프로젝트.")
public record MyProjectResponse(
        @Schema(description = "프로젝트 식별자.", example = "12") Integer projectId,
        @Schema(description = "프로젝트 이름.", example = "수제 비누 가게") String name,
        @Schema(description = "가장 최근 진단서 식별자. 없으면 null이다.") Integer latestDiagnosisId,
        @Schema(description = "가장 최근에 진단한 시각. 없으면 null이다.")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") OffsetDateTime latestDiagnosedAt,
        @Schema(description = "이 프로젝트로 연 방 수.", example = "4") int roomCount) {
}
