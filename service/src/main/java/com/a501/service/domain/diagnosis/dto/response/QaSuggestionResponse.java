package com.a501.service.domain.diagnosis.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 저장 전 확인 문장 후보 한 건. (`R-REQ-04`)
 *
 * <p><b>식별자와 상태가 없습니다.</b> 아직 저장하지 않은 후보라 붙일 식별자가 없습니다. 사용자가
 * 고르고 고친 뒤 요청서 저장이 확정하며, 그때 {@link QaCriterionResponse} 가 됩니다.
 *
 * @param testCase 의뢰인이 직접 실행해 통과 여부를 판단할 문장
 */
@Schema(description = "저장 전 확인 문장 후보. 아직 저장하지 않아 식별자가 없다.")
public record QaSuggestionResponse(
        @Schema(
                        description = "직접 실행해 통과 여부를 판단할 문장.",
                        example = "로그인하지 않고 관리자 주소로 들어가면 로그인 화면이 나타난다.")
                String testCase) {}
