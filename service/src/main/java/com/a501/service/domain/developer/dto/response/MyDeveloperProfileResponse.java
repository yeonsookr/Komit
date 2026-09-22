package com.a501.service.domain.developer.dto.response;

import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.review.dto.response.ReviewResponse;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 내 프로필 편집 화면이 쓰는 값. 목 명세 {@code MyDeveloperProfileResponse}와 같습니다.
 * 남에게 보이는 개발자 상세에 줄 단위 가격표 전부와 받은 리뷰를 더한 모양이며, 유형별 최저가는 담지 않습니다.
 */
@Schema(description = "내 프로필 편집 화면이 쓰는 값.")
public record MyDeveloperProfileResponse(
        @Schema(description = "개발자 식별자. 사용자 식별자가 아니다.", example = "3") Integer developerId,
        @Schema(description = "화면에 표시하는 이름.", example = "이준") String name,
        @Schema(description = "프로필 사진 주소. 직접 올린 사진이 있으면 그 사진의 임시 주소, 없으면 카카오 사진 주소, 둘 다 없으면 null이다.")
        String profileImageUrl,
        @Schema(description = "개발자가 적은 한 줄 소개.", example = "간단한 상담도 받습니다. 용어부터 풀어서 설명합니다.") String intro,
        @Schema(description = "개발자 소개 글. 적지 않았으면 null이다.") String description,
        @Schema(description = "경력 연차. 등록하지 않았으면 null이다.", example = "5.0") BigDecimal yearsOfExperience,
        @Schema(description = "주로 담당하는 개발 분야. 등록하지 않았으면 null이다.") CodeResponse field,
        @Schema(description = "주로 활동하는 시간대의 시작. 정하지 않았으면 null이다.", example = "10:00", type = "string")
        @JsonFormat(pattern = "HH:mm") LocalTime activeFrom,
        @Schema(description = "주로 활동하는 시간대의 끝. 시작보다 이르면 자정을 넘기는 것으로 본다.", example = "19:00", type = "string")
        @JsonFormat(pattern = "HH:mm") LocalTime activeTo,
        @Schema(description = "증빙이 확인된 경력을 하나라도 가지고 있는지.", example = "true")
        @JsonProperty("isCareerVerified") boolean isCareerVerified,
        @Schema(description = "개발력 평점과 리뷰 수.") RatingResponse devRating,
        @Schema(description = "설명력 평점과 리뷰 수.") RatingResponse explanatoryRating,
        @Schema(description = "다룰 수 있는 기술. 등록 전이면 빈 배열이다.") List<CodeResponse> techCategories,
        @Schema(description = "등록한 경력. 최근 경력이 먼저다.") List<MyCareerResponse> careers,
        @Schema(description = "등록한 작업물. 등록한 순서다.") List<MyPortfolioItemResponse> portfolioItems,
        @Schema(description = "등록한 가격표 전부.") List<PriceCatalogItemResponse> priceCatalog,
        @Schema(description = "받은 리뷰. 새로운 것이 먼저다.") List<ReviewResponse> reviews,
        @Schema(description = "답글을 단 리뷰 수.", example = "1") int replyCount,
        @Schema(description = "마지막으로 저장한 시각.", example = "2026-08-14T18:20:00+09:00") @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") OffsetDateTime updatedAt) {
}
