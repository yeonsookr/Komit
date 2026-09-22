package com.a501.service.domain.diagnosis;

import java.util.Arrays;

/**
 * 검사 결과가 쓰는 여덟 분류와 코드북의 이름을 잇는다. (R-SCAN-07)
 *
 * <p>결과 파일은 영문 이름으로 오고 {@code dynamic_codes} 는 화면에 그대로 보여줄 한글 이름을 갖는다. 코드 식별자를 여기 적지 않는 것은 번호가 시드에 달려 있기 때문이다.
 */
public enum IssueCategory {
    SECURITY("보안"),
    COMPLIANCE("위법"),
    PAYMENT("결제"),
    DATA("데이터"),
    COST("비용"),
    HANDOVER("인계"),
    AI_RESIDUE("AI 잔재"),
    ETC("기타");

    public static final String GROUP = "ISSUE_CATEGORY";

    private final String codeName;

    IssueCategory(String codeName) {
        this.codeName = codeName;
    }

    public String codeName() {
        return codeName;
    }

    /** 모르는 분류는 기타로 본다. 검사 도구가 분류를 늘려도 결함을 잃지 않는다. */
    public static IssueCategory of(String name) {
        return Arrays.stream(values())
                .filter(category -> category.name().equals(name))
                .findFirst()
                .orElse(ETC);
    }
}
