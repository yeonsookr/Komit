package com.a501.service.domain.consulting.dto;

import java.util.List;

/**
 * 세션 요약의 내용. {@code consulting_sessions.summary} 에 이 모양 그대로 남는다.
 *
 * <p>화면에 나가는 모양과 같다. AI 가 붙여 주는 근거 발화 순번과 검토 표시는 AI 내부 값이라 여기
 * 없다 (`docs/ai/transcript-evidence-contract.md` 5절). 변경 후보의 식별자와 반영 여부는 Service
 * 가 붙인다 (`docs/ai/summary-change-contract.md` 4절).
 *
 * @param discussed 논의한 것
 * @param decided 결정된 것
 * @param nextActions 진행할 것
 * @param changes 요청서에 반영할 수 있는 변경 후보
 */
public record SummaryContent(
        List<Item> discussed, List<Item> decided, List<Item> nextActions, List<Change> changes) {

    /** 말이 하나도 없던 세션의 요약. */
    public static SummaryContent empty() {
        return new SummaryContent(List.of(), List.of(), List.of(), List.of());
    }

    /**
     * 요약 항목 하나.
     *
     * @param title 항목 제목
     * @param detail 덧붙인 설명. 없으면 null
     */
    public record Item(String title, String detail) {}

    /**
     * 변경 후보 하나.
     *
     * @param changeId 이 세션 안의 후보 번호. 1 부터 붙는다
     * @param kind 이미 있는 요구사항을 고치는지, 새로 넣는지
     * @param issueId 고칠 요구사항. 새로 넣는 것이면 null
     * @param title 반영할 요구사항 문장
     * @param detail 왜 바꾸는지. 없으면 null
     * @param isApplied 이미 요청서에 반영했는지
     */
    public record Change(
            Integer changeId, Kind kind, Integer issueId, String title, String detail, boolean isApplied) {

        public Change applied() {
            return new Change(changeId, kind, issueId, title, detail, true);
        }
    }

    public enum Kind {
        UPDATE,
        ADD
    }
}
