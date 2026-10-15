package com.a501.service.domain.diagnosis.dto.response;

import com.a501.service.domain.diagnosis.entity.QaCriterion;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 확인 문장 하나. (`R-QA-01`)
 *
 * <p>요구사항과 계약의 작업이 같은 문장을 씁니다. 요청서에서 만든 문장이 그대로 계약의 완료
 * 조건이 되므로 한 모델로 둡니다.
 *
 * @param qaCriterionId 확인 문장 id
 * @param testCase 의뢰인이 직접 실행해 통과 여부를 판단할 문장
 * @param condition 문장을 확인할 조건
 * @param status 확인 결과 코드값
 * @param issueId 이 문장이 나온 요구사항. 계약에서 새로 만든 문장이면 {@code null}
 */
@Schema(description = "확인 문장 하나. 요구사항과 계약의 작업이 같은 모델을 쓴다.")
public record QaCriterionResponse(
        @Schema(description = "확인 문장 식별자.", example = "201") Integer qaCriterionId,
        @Schema(
                        description = "직접 실행해 통과 여부를 판단할 문장.",
                        example = "로그인하지 않고 관리자 주소로 들어가면 로그인 화면이 나타난다")
                String testCase,
        @Schema(description = "문장을 확인할 조건. 없으면 null 이다.") String condition,
        @Schema(
                        description = "확인 결과. UNRESOLVED 는 미해결, RESOLVED 는 해결이다.",
                        example = "UNRESOLVED")
                String status,
        @Schema(description = "이 문장이 나온 요구사항 식별자. 없으면 null 이다.", example = "312")
                Integer issueId) {

    /**
     * 엔티티를 응답으로 바꿉니다.
     *
     * @param entity 확인 문장
     * @return 응답 DTO
     */
    public static QaCriterionResponse from(QaCriterion entity) {
        return new QaCriterionResponse(
                entity.getId(),
                entity.getTestCase(),
                entity.getCondition(),
                entity.getStatus(),
                entity.getIssueId());
    }
}
