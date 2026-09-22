package com.a501.service.domain.diagnosis.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "결함 목록.")
public record IssueListResponse(
        @Schema(description = "현재 페이지에 속한 결함. 결과가 없으면 빈 배열이다.")
        List<IssueResponse> content,
        @Schema(description = "현재 페이지 번호. 1부터 시작한다.", example = "1") int page,
        @Schema(description = "한 페이지에 담긴 최대 항목 수.", example = "20") int size,
        @Schema(description = "조건에 해당하는 전체 결함 수.", example = "11") long totalElements,
        @Schema(description = "전체 페이지 수.", example = "1") int totalPages) {
    public static IssueListResponse of(Page<IssueResponse> found, int page) {
        return new IssueListResponse(
                found.getContent(), page, found.getSize(), found.getTotalElements(), found.getTotalPages());
    }
}
