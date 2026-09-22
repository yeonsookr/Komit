package com.a501.service.domain.developer.dto.response;

import com.a501.service.domain.developer.entity.PortfolioItem;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 남에게 보이는 작업물 하나. 목 명세 {@code PortfolioItemResponse}와 같습니다.
 *
 * <p>{@code thumbnailUrl}은 저장된 값이 아니라 응답을 만들 때마다 새로 만드는 임시 주소입니다.
 */
@Schema(description = "개발자가 올린 작업물 하나.")
public record PortfolioItemResponse(
        @Schema(description = "포트폴리오 식별자.", example = "7") Integer portfolioItemId,
        @Schema(description = "작업물 제목.", example = "동네 필라테스 수업") String title,
        @Schema(description = "설명. 적지 않았으면 null이다.") String description,
        @Schema(description = "배포 사이트나 저장소 주소. 없으면 null이고, 화면은 그 자리에 '비공개'라고 적는다.", example = "https://example.com")
        String linkUrl,
        @Schema(description = "썸네일 파일 식별자. 올리지 않았으면 null이다.") Integer thumbnailFileId,
        @Schema(description = "화면에 그대로 거는 사진 주소. 한동안만 쓸 수 있는 임시 주소다. 사진이 없으면 null이다.") String thumbnailUrl) {

    public static PortfolioItemResponse of(PortfolioItem item, String thumbnailUrl) {
        return new PortfolioItemResponse(
                item.getId(), item.getTitle(), item.getDescription(), item.getLinkUrl(), item.getThumbnailId(), thumbnailUrl);
    }
}
