package com.a501.service.domain.developer.dto.request;

import com.a501.service.domain.developer.dto.request.validation.CareerPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 저장할 경력 한 줄. 목 명세 {@code WriteCareerRequest}와 같습니다.
 *
 * <p>날짜는 목과 같이 {@code YYYY-MM-DD} 문자열로 받고 형식 위반은 {@code @Pattern}이 422로 거릅니다. 종료일이 시작일보다 빠른 것은
 * {@link CareerPeriod}가 {@code careers.N.endDate} 필드 오류로 냅니다.
 */
@Schema(description = "저장할 경력 한 줄.")
@CareerPeriod
public record WriteCareerRequest(
        @Schema(description = "이미 있는 경력이면 그 식별자, 새로 추가하는 줄이면 null이다.", example = "41")
        Integer careerId,
        @Schema(description = "회사 이름. 50자까지.", example = "카카오스타일")
        @NotNull @Size(max = 50) String company,
        @Schema(description = "맡았던 일. 100자까지.", example = "백엔드 · 주문/정산 서비스")
        @NotNull @Size(max = 100) String role,
        @Schema(description = "시작일. YYYY-MM-DD.", example = "2021-03-01")
        @NotNull @Pattern(regexp = DATE_PATTERN) String startDate,
        @Schema(description = "종료일. YYYY-MM-DD. 지금도 다니는 중이면 null이다.", example = "2024-08-31")
        @Pattern(regexp = DATE_PATTERN) String endDate,
        @Schema(description = "증빙 서류 파일 식별자. 기존 식별자면 유지, 새 식별자면 교체, null이면 붙이지 않는다.", example = "512")
        Integer evidenceFileId) {

    /** 목 명세 {@code DATE_PATTERN}과 같습니다. */
    public static final String DATE_PATTERN = "^\\d{4}-\\d{2}-\\d{2}$";

    /** 형식 검증을 통과한 시작일. 통과 전에는 부르지 않습니다. */
    public LocalDate startDateValue() {
        return LocalDate.parse(startDate);
    }

    /** 형식 검증을 통과한 종료일. 없으면 null입니다. */
    public LocalDate endDateValue() {
        return endDate == null ? null : LocalDate.parse(endDate);
    }
}
