package com.a501.service.domain.developer.dto.response;

import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.developer.dto.DeveloperSearchRow;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * 남에게 보이는 개발자 프로필 상세. 목 명세 {@code DeveloperDetailResponse}와 같습니다.
 * 목록 한 줄({@link DeveloperSummaryResponse})의 값에 소개글, 활동 시간대, 경력, 작업물, 가격표 전부를 더한 모양입니다.
 * 자바 record는 상속이 없어 목록 필드를 그대로 다시 적습니다.
 */
@Schema(description = "개발자 프로필 상세. 목록에 쓰는 값에 소개글과 이력을 더한다.")
public record DeveloperDetailResponse(
        @Schema(description = "개발자 식별자. 사용자 식별자가 아니다.", example = "3") Integer developerId,
        @Schema(description = "화면에 표시하는 이름.", example = "이준") String name,
        @Schema(description = "직접 올린 프로필 사진의 파일 식별자. 없으면 null이다.") Integer profileImageFileId,
        @Schema(description = "프로필 사진 주소. 직접 올린 사진이 있으면 그 사진의 임시 주소, 없으면 카카오 사진 주소, 둘 다 없으면 null이다.")
        String profileImageUrl,
        @Schema(description = "개발자가 적은 한 줄 소개.") String intro,
        @Schema(description = "주로 담당하는 개발 분야. 등록하지 않았으면 null이다.") CodeResponse field,
        @Schema(description = "경력 연차. 등록하지 않았으면 null이다.", example = "5.0") BigDecimal yearsOfExperience,
        @Schema(description = "증빙이 확인된 경력을 하나라도 가지고 있는지.", example = "true")
        @JsonProperty("isCareerVerified") boolean isCareerVerified,
        @Schema(description = "개발력 평점과 리뷰 수.") RatingResponse devRating,
        @Schema(description = "설명력 평점과 리뷰 수.") RatingResponse explanatoryRating,
        @Schema(description = "다룰 수 있는 기술. 등록 전이면 빈 배열이다.") List<CodeResponse> techCategories,
        @Schema(description = "작업 유형별 최저가. 가격표를 등록하지 않은 유형은 빠진다.") List<TaskPriceResponse> prices,
        @Schema(description = "이 개발자가 마친 계약 수.", example = "23") int contractCount,
        @Schema(description = "개발자 소개 글. 적지 않았으면 null이다.", example = "작은 가게 서비스를 주로 맡습니다.") String description,
        @Schema(description = "주로 활동하는 시간대의 시작. 정하지 않았으면 null이다.", example = "10:00", type = "string")
        @JsonFormat(pattern = "HH:mm") LocalTime activeFrom,
        @Schema(description = "주로 활동하는 시간대의 끝. 시작보다 이르면 자정을 넘기는 것으로 본다.", example = "19:00", type = "string")
        @JsonFormat(pattern = "HH:mm") LocalTime activeTo,
        @Schema(description = "등록한 경력. 최근 경력이 먼저다.") List<CareerResponse> careers,
        @Schema(description = "등록한 작업물. 등록한 순서다.") List<PortfolioItemResponse> portfolioItems,
        @Schema(description = "등록한 가격표 전부. 컨설팅이 먼저 오고 그 안에서 싼 것부터다. 없으면 빈 배열이다.")
        List<PriceCatalogItemResponse> priceCatalog) {

    public static DeveloperDetailResponse of(
            DeveloperSummaryResponse summary,
            DeveloperSearchRow row,
            List<CareerResponse> careers,
            List<PortfolioItemResponse> portfolioItems,
            List<PriceCatalogItemResponse> priceCatalog) {
        return new DeveloperDetailResponse(
                summary.developerId(),
                summary.name(),
                summary.profileImageFileId(),
                summary.profileImageUrl(),
                summary.intro(),
                summary.field(),
                summary.yearsOfExperience(),
                summary.isCareerVerified(),
                summary.devRating(),
                summary.explanatoryRating(),
                summary.techCategories(),
                summary.prices(),
                summary.contractCount(),
                row.description(),
                row.activeFrom(),
                row.activeTo(),
                careers,
                portfolioItems,
                priceCatalog);
    }
}
