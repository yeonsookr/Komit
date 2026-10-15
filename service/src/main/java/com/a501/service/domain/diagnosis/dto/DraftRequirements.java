package com.a501.service.domain.diagnosis.dto;

import java.util.List;

/**
 * 개발 계약 초안이 쓰는 요청서. 계약 도메인이 진단 엔티티 없이 요청서를 읽도록 넘긴다.
 * (BACKEND_CONVENTIONS 7.1)
 *
 * @param requested 요청서에 담긴 요구사항. 계약서에 모두 그대로 들어간다
 * @param leftOut 진단 직후 의뢰인이 고르지 않은 요구사항. AI 초안의 참고 입력으로만 쓴다
 */
public record DraftRequirements(List<Requested> requested, List<LeftOut> leftOut) {

    public DraftRequirements {
        requested = List.copyOf(requested);
        leftOut = List.copyOf(leftOut);
    }

    /**
     * 요청서의 요구사항 하나.
     *
     * @param issueId 요구사항 id
     * @param title 이름
     * @param detail 설명. 없으면 {@code null}
     * @param qaCriteria 의뢰인이 요청서에 확정한 확인 문장
     */
    public record Requested(Integer issueId, String title, String detail, List<String> qaCriteria) {
        public Requested {
            qaCriteria = List.copyOf(qaCriteria);
        }
    }

    /**
     * 고르지 않은 요구사항 하나. 사유는 받지 않는다.
     *
     * @param issueId 요구사항 id
     * @param title 이름
     */
    public record LeftOut(Integer issueId, String title) {}
}
