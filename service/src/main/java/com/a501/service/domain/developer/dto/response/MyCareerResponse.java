package com.a501.service.domain.developer.dto.response;

import com.a501.service.common.time.SeoulTime;
import com.a501.service.domain.developer.entity.Career;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 내 프로필 편집 화면이 쓰는 경력 한 줄. 목 명세 {@code MyCareerResponse}와 같습니다.
 * 확인 전 증빙은 파일 식별자와 이름을 함께 내려주고, 확인을 마쳐 원본을 지운 경력은 두 값이 모두 null입니다.
 */
@Schema(description = "내 프로필 편집 화면이 쓰는 경력 한 줄.")
public record MyCareerResponse(
        @Schema(description = "경력 식별자.", example = "41") Integer careerId,
        @Schema(description = "회사 이름.", example = "카카오스타일") String company,
        @Schema(description = "맡았던 일.", example = "백엔드 · 주문/정산 서비스") String role,
        @Schema(description = "시작일.", example = "2021-03-01") LocalDate startDate,
        @Schema(description = "종료일. 지금도 다니는 중이면 null이다.", example = "2024-08-31") LocalDate endDate,
        @Schema(description = "증빙이 확인된 경력인지.", example = "true") @JsonProperty("isVerified") boolean isVerified,
        @Schema(description = "증빙을 확인한 시각. 확인 전이면 null이다.", example = "2026-03-04T10:00:00+09:00") @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") OffsetDateTime verifiedAt,
        @Schema(description = "확인 전 증빙 파일 식별자. 확인을 마치면 null이다.", example = "512") Integer evidenceFileId,
        @Schema(description = "확인 전 증빙의 원래 파일명. evidenceFileId와 함께 값이 있거나 함께 null이다.", example = "재직증명서.pdf")
        String evidenceFileName) {

    public static MyCareerResponse of(Career career, String evidenceFileName) {
        boolean pending = !career.isVerified() && career.getFileId() != null;
        return new MyCareerResponse(
                career.getId(),
                career.getCompany(),
                career.getRole(),
                career.getStartDate(),
                career.getEndDate(),
                career.isVerified(),
                SeoulTime.of(career.getVerifiedAt()),
                pending ? career.getFileId() : null,
                pending ? evidenceFileName : null);
    }
}
