package com.a501.service.domain.developer.dto.response;

import com.a501.service.common.time.SeoulTime;
import com.a501.service.domain.developer.entity.Career;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 남에게 보이는 개발자 경력 한 줄. 목 명세 {@code CareerResponse}와 같으며 증빙 파일 정보는 담지 않습니다. */
@Schema(description = "개발자 경력 한 줄.")
public record CareerResponse(
        @Schema(description = "경력 식별자.", example = "41") Integer careerId,
        @Schema(description = "회사 이름.", example = "카카오스타일") String company,
        @Schema(description = "맡았던 일.", example = "백엔드 · 주문/정산 서비스") String role,
        @Schema(description = "시작일.", example = "2021-03-01") LocalDate startDate,
        @Schema(description = "종료일. 지금도 다니는 중이면 null이다.", example = "2024-08-31") LocalDate endDate,
        @Schema(description = "증빙이 확인된 경력인지. 거짓이면 화면에 '확인 중'으로 나온다.", example = "true")
        @JsonProperty("isVerified") boolean isVerified,
        @Schema(description = "증빙을 확인한 시각. 확인 전이면 null이다.", example = "2026-03-04T10:00:00+09:00")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") OffsetDateTime verifiedAt) {

    public static CareerResponse from(Career career) {
        return new CareerResponse(
                career.getId(),
                career.getCompany(),
                career.getRole(),
                career.getStartDate(),
                career.getEndDate(),
                career.isVerified(),
                SeoulTime.of(career.getVerifiedAt()));
    }
}
