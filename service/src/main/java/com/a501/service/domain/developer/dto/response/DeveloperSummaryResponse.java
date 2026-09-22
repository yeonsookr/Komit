package com.a501.service.domain.developer.dto.response;

import com.a501.service.domain.code.dto.CodeResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** 목록에 한 줄로 나가는 개발자. 목 명세 {@code DeveloperSummaryResponse}와 같으며 찾기와 추천이 같은 모양을 씁니다. */
@Schema(description = "목록에 한 줄로 나가는 개발자. 찾기와 추천이 같은 모양을 쓴다.")
public record DeveloperSummaryResponse(
        @Schema(description = "개발자 식별자. 사용자 식별자가 아니다.", example = "3") Integer developerId,
        @Schema(description = "화면에 표시하는 이름.", example = "이준") String name,
        @Schema(description = "직접 올린 프로필 사진의 파일 식별자. 없으면 null이다.") Integer profileImageFileId,
        @Schema(description = "프로필 사진 주소. 직접 올린 사진이 있으면 그 사진의 임시 주소, 없으면 카카오 사진 주소, 둘 다 없으면 null이다.")
        String profileImageUrl,
        @Schema(description = "개발자가 적은 한 줄 소개.", example = "간단한 상담도 받습니다. 용어부터 풀어서 설명합니다.") String intro,
        @Schema(description = "주로 담당하는 개발 분야. 코드 분류 DEV_FIELD의 항목이며 한 명에 하나다. 등록하지 않았으면 null이다.")
        CodeResponse field,
        @Schema(description = "경력 연차. 등록하지 않았으면 null이다.", example = "5.0") BigDecimal yearsOfExperience,
        @Schema(description = "증빙이 확인된 경력을 하나라도 가지고 있는지.", example = "true")
        @JsonProperty("isCareerVerified") boolean isCareerVerified,
        @Schema(description = "개발력 평점과 리뷰 수.") RatingResponse devRating,
        @Schema(description = "설명력 평점과 리뷰 수.") RatingResponse explanatoryRating,
        @Schema(description = "다룰 수 있는 기술. 코드 분류 TECH_CATEGORY의 항목이며 여럿일 수 있다. 등록 전이면 빈 배열이다.")
        List<CodeResponse> techCategories,
        @Schema(description = "작업 유형별 최저가. 가격표를 등록하지 않은 유형은 빠진다.") List<TaskPriceResponse> prices,
        @Schema(description = "이 개발자가 마친 계약 수. 컨설팅과 개발을 합쳐 세며 완료된 계약만 든다.", example = "23") int contractCount) {
}
