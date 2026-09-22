package com.a501.service.domain.landing.dto.response;

import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.developer.dto.response.DeveloperSummaryResponse;
import com.a501.service.domain.developer.dto.response.TaskPriceResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * 랜딩에 노출하는 추천 개발자 한 명. 목 명세 {@code FeaturedDeveloperResponse}와 같습니다.
 * 로그인 전에도 보이는 공개 정보라 목록 카드보다 필드가 적고, 평점은 리뷰 수 없이 점수만 담습니다.
 */
@Schema(description = "랜딩에 노출하는 추천 개발자 한 명.")
public record FeaturedDeveloperResponse(
        @Schema(description = "개발자 식별자. 사용자 식별자가 아니다.", example = "3") Integer developerId,
        @Schema(description = "화면에 표시하는 이름.", example = "이준") String name,
        @Schema(description = "직접 올린 프로필 사진의 파일 식별자. 올린 사진이 없으면 null이고, 그때 화면은 이름 첫 글자를 보여준다.")
        Integer profileImageFileId,
        @Schema(description = "프로필 사진 주소. 직접 올린 사진이 있으면 그 사진의 임시 주소, 없으면 카카오 사진 주소, 둘 다 없으면 null이다.")
        String profileImageUrl,
        @Schema(description = "개발자가 적은 한 줄 소개.", example = "간단한 상담도 받습니다.") String intro,
        @Schema(description = "경력 연차. 등록하지 않았으면 null이다.", example = "5.0") BigDecimal yearsOfExperience,
        @Schema(description = "주로 담당하는 개발 분야. 코드 분류 DEV_FIELD의 항목이며 등록하지 않았으면 null이다.") CodeResponse field,
        @Schema(description = "개발력 평균 평점. 받은 리뷰가 없으면 null이다.", example = "4.4") Double devRating,
        @Schema(description = "설명력 평균 평점. 받은 리뷰가 없으면 null이다.", example = "4.9") Double explanatoryRating,
        @Schema(description = "작업 유형별 최저가. 가격표를 등록하지 않은 유형은 빠진다. 무료 상담은 가격표에 없으므로 담기지 않는다.")
        List<TaskPriceResponse> prices) {

    public static FeaturedDeveloperResponse from(DeveloperSummaryResponse summary) {
        return new FeaturedDeveloperResponse(
                summary.developerId(),
                summary.name(),
                summary.profileImageFileId(),
                summary.profileImageUrl(),
                summary.intro(),
                summary.yearsOfExperience(),
                summary.field(),
                summary.devRating().score(),
                summary.explanatoryRating().score(),
                summary.prices());
    }
}
