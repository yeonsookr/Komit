package com.a501.service.domain.developer.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * 내 프로필 저장 요청. 목 명세 {@code WriteDeveloperProfileRequest}와 같습니다. 화면의 저장 버튼 하나가 이 요청 하나를 보내며,
 * 목록에서 빠진 줄은 지운 것으로 봅니다.
 *
 * <p>목록 필드는 필수이지만 빈 배열을 허용합니다. 시간은 목과 같이 {@code HH:MM} 문자열로 받습니다.
 */
@Schema(description = "내 프로필 저장 요청. 화면의 저장 버튼 하나가 이 요청 하나를 보낸다.")
public record WriteDeveloperProfileRequest(
        @Schema(description = "한 줄 소개. 1자 이상 100자까지.", example = "간단한 상담도 받습니다. 용어부터 풀어서 설명합니다.")
        @NotNull @Size(min = 1, message = "{komit.minLength}") @Size(max = 100) String intro,
        @Schema(description = "소개 글. 1000자까지.", example = "AI로 만든 걸 실제로 돌아가게 만드는 일을 주로 합니다.")
        @JsonProperty(required = true) @Size(max = 1000) String description,
        @Schema(description = "주로 담당하는 개발 분야 코드 식별자. 코드 분류 DEV_FIELD의 항목이다.", example = "42")
        @JsonProperty(required = true) Integer fieldCodeId,
        @Schema(description = "경력 연차. 0 이상 99.9 이하.", example = "5.0")
        @JsonProperty(required = true) @DecimalMin("0") @DecimalMax("99.9") BigDecimal yearsOfExperience,
        @Schema(description = "주로 활동하는 시간대의 시작. HH:MM.", example = "10:00")
        @JsonProperty(required = true) @Pattern(regexp = TIME_PATTERN) String activeFrom,
        @Schema(description = "주로 활동하는 시간대의 끝. HH:MM.", example = "19:00")
        @JsonProperty(required = true) @Pattern(regexp = TIME_PATTERN) String activeTo,
        @Schema(description = "다룰 수 있는 기술의 코드 식별자. 코드 분류 TECH_CATEGORY의 항목이며 보낸 목록이 그대로 저장된다.")
        @NotNull List<@NotNull Integer> techCodeIds,
        @Schema(description = "경력 전부. 보낸 목록에 없는 기존 경력은 지운 것으로 본다.")
        @NotNull List<@Valid @NotNull WriteCareerRequest> careers,
        @Schema(description = "작업물 전부. 보낸 목록에 없는 기존 작업물은 지운 것으로 본다. 새로 추가한 줄은 뒤에 붙는다.")
        @NotNull List<@Valid @NotNull WritePortfolioItemRequest> portfolioItems,
        @Schema(description = "가격표 전부. 보낸 목록에 없는 기존 줄은 지운 것으로 본다.")
        @NotNull List<@Valid @NotNull WritePriceRequest> prices) {

    /** 목 명세 {@code TIME_PATTERN}과 같습니다. */
    public static final String TIME_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";

    public LocalTime activeFromValue() {
        return activeFrom == null ? null : LocalTime.parse(activeFrom);
    }

    public LocalTime activeToValue() {
        return activeTo == null ? null : LocalTime.parse(activeTo);
    }
}
