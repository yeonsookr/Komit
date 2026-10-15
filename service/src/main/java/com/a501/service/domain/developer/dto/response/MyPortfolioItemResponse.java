package com.a501.service.domain.developer.dto.response;

import com.a501.service.domain.developer.entity.PortfolioItem;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 프로필 편집 화면이 쓰는 작업물 하나. 목 명세 {@code MyPortfolioItemResponse}와 같습니다.
 *
 * <p>{@code thumbnailUrl}은 저장된 값이 아니라 응답을 만들 때마다 새로 만드는 임시 주소입니다.
 */
@Schema(description = "내 프로필 편집 화면이 쓰는 작업물 하나.")
public record MyPortfolioItemResponse(
        @Schema(description = "작업물 식별자.", example = "7") Integer portfolioItemId,
        @Schema(description = "작업물 제목.", example = "미미헤어샵 예약") String title,
        @Schema(description = "설명. 적지 않았으면 null이다.", example = "예약이 저장되지 않던 문제를 고쳤습니다.") String description,
        @Schema(description = "배포 사이트나 저장소 주소. 없으면 null이다.", example = "https://hairshop-mimi.com") String linkUrl,
        @Schema(description = "현재 붙어 있는 썸네일 파일 식별자. 사진이 없으면 null이다.", example = "512") Integer thumbnailFileId,
        @Schema(description = "화면에 그대로 거는 사진 주소. 한동안만 쓸 수 있는 임시 주소다. 사진이 없으면 null이다.",
                example = "https://cdn.komit.example.com/portfolio/512.png") String thumbnailUrl) {

    public static MyPortfolioItemResponse of(PortfolioItem item, String thumbnailUrl) {
        return new MyPortfolioItemResponse(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getLinkUrl(),
                item.getThumbnailId(),
                thumbnailUrl);
    }
}
