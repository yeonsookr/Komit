package com.a501.service.domain.diagnosis.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 확인 문장 후보 목록.
 *
 * <p><b>DB 페이지가 아니다.</b> 한 번 부를 때마다 모델이 새로 만들고 저장하지 않으므로 넘길 다음
 * 쪽이 없다. 그래도 목 명세가 default API 의 목록 형식을 그대로 쓰므로 페이지 칸을 채운다.
 * (`BACKEND_CONVENTIONS` 11.1 — 명세에 있는 필드를 빼지 않는다)
 *
 * <p>값은 {@link #of(List)} 가 목록 크기에서 뽑는다. 숫자를 직접 적으면 후보 수가 바뀔 때 한쪽만
 * 고치게 된다.
 *
 * @param content 후보. 목 명세상 언제나 세 건이다 (`R-REQ-04`, `R-CON-22`)
 */
@Schema(description = "확인 문장 후보 목록. 저장 전이라 넘길 다음 쪽이 없다.")
public record QaSuggestionListResponse(
        @Schema(description = "저장 전 확인 문장 후보 세 건.") List<QaSuggestionResponse> content,
        @Schema(description = "현재 페이지 번호. 후보는 한 페이지만 반환한다.", example = "1") int page,
        @Schema(description = "한 페이지의 후보 개수.", example = "3") int size,
        @Schema(description = "전체 후보 개수.", example = "3") int totalElements,
        @Schema(description = "전체 페이지 수.", example = "1") int totalPages) {

    public static QaSuggestionListResponse of(List<QaSuggestionResponse> content) {
        return new QaSuggestionListResponse(content, 1, content.size(), content.size(), 1);
    }
}
