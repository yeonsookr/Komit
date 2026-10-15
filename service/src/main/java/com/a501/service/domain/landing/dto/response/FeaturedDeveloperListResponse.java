package com.a501.service.domain.landing.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 추천 개발자 목록. 목 명세 {@code FeaturedDeveloperListResponse}와 같습니다.
 * 노출 수가 적고 고정이라 페이지를 넘길 일이 없어 {@code content}만 담습니다.
 */
@Schema(description = "추천 개발자 목록. 페이지 정보 없이 content만 담는다.")
public record FeaturedDeveloperListResponse(
        @Schema(description = "추천 개발자 목록. 노출할 개발자가 없으면 빈 배열이다.") List<FeaturedDeveloperResponse> content) {
}
