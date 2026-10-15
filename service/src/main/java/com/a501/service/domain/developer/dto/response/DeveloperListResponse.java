package com.a501.service.domain.developer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 개발자 찾기 목록. 목 명세 {@code DeveloperListResponse}와 같습니다. 기준 API의 목록 응답에 {@code totalDevelopers}가 더 있습니다.
 *
 * @param content         현재 페이지의 개발자. 조건에 맞는 사람이 없으면 빈 배열입니다.
 * @param page            요청한 페이지 번호(1부터).
 * @param size            한 페이지에 담을 최대 항목 수.
 * @param totalElements   조건에 맞는 개발자 수. 화면의 'N명 중 M명'에서 M입니다.
 * @param totalPages      전체 페이지 수. 항목이 없으면 0입니다.
 * @param totalDevelopers 조건을 걸지 않았을 때의 전체 개발자 수. 화면의 'N명 중 M명'에서 N입니다.
 */
@Schema(description = "개발자 찾기 목록.")
public record DeveloperListResponse(
        @Schema(description = "현재 페이지에 속한 개발자. 조건에 맞는 사람이 없으면 빈 배열이다.") List<DeveloperSummaryResponse> content,
        @Schema(description = "현재 페이지 번호. 1부터 시작한다.", example = "1") int page,
        @Schema(description = "한 페이지에 담긴 최대 항목 수.", example = "20") int size,
        @Schema(description = "조건에 맞는 개발자 수. 화면의 'N명 중 M명'에서 M에 해당한다.", example = "5") long totalElements,
        @Schema(description = "전체 페이지 수.", example = "1") int totalPages,
        @Schema(description = "조건을 걸지 않았을 때의 전체 개발자 수. 화면의 'N명 중 M명'에서 N에 해당한다.", example = "128")
        long totalDevelopers) {

    /**
     * Spring Data 페이지 결과를 계약 형태로 바꿉니다.
     *
     * @param found           0부터 시작하는 번호로 조회한 페이지 결과
     * @param page            클라이언트가 요청한 1부터 시작하는 페이지 번호
     * @param totalDevelopers 조건 없는 전체 개발자 수
     */
    public static DeveloperListResponse of(Page<DeveloperSummaryResponse> found, int page, long totalDevelopers) {
        return new DeveloperListResponse(
                found.getContent(), page, found.getSize(), found.getTotalElements(), found.getTotalPages(), totalDevelopers);
    }

    /** 조건에 맞는 사람이 하나도 없을 때. 이름 검색에 맞는 사용자가 없어 DB 조회를 건너뛸 때 씁니다. */
    public static DeveloperListResponse empty(int page, int size, long totalDevelopers) {
        return new DeveloperListResponse(List.of(), page, size, 0, 0, totalDevelopers);
    }
}
