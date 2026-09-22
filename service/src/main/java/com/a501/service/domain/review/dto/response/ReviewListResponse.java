package com.a501.service.domain.review.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 개발자가 받은 리뷰 목록. 목 명세 {@code ReviewListResponse}와 같으며 기준 API의 목록 응답 형태입니다.
 *
 * @param content       현재 페이지의 리뷰. 없으면 빈 배열입니다.
 * @param page          요청한 페이지 번호(1부터).
 * @param size          한 페이지에 담을 최대 항목 수.
 * @param totalElements 전체 리뷰 수. 개발자 프로필의 {@code devRating.reviewCount}와 언제나 같습니다.
 * @param totalPages    전체 페이지 수. 항목이 없으면 0입니다.
 */
@Schema(description = "개발자가 받은 리뷰 목록.")
public record ReviewListResponse(
        @Schema(description = "현재 페이지에 속한 리뷰. 없으면 빈 배열이다.") List<ReviewResponse> content,
        @Schema(description = "현재 페이지 번호. 1부터 시작한다.", example = "1") int page,
        @Schema(description = "한 페이지에 담긴 최대 항목 수.", example = "20") int size,
        @Schema(description = "전체 리뷰 수. 개발자 프로필의 devRating.reviewCount와 언제나 같은 값이다.", example = "20") long totalElements,
        @Schema(description = "전체 페이지 수.", example = "1") int totalPages) {

    /**
     * Spring Data 페이지 결과를 계약 형태로 바꿉니다.
     *
     * @param found 0부터 시작하는 번호로 조회한 페이지 결과
     * @param page  클라이언트가 요청한 1부터 시작하는 페이지 번호
     */
    public static ReviewListResponse of(Page<ReviewResponse> found, int page) {
        return new ReviewListResponse(found.getContent(), page, found.getSize(), found.getTotalElements(), found.getTotalPages());
    }
}
