package com.a501.service.domain.diagnosis;

/** 결함 처리 상태. {@code system_enums} 의 {@code ISSUE_STATUS} 묶음과 같은 값이다. */
public enum IssueStatus {
    UNRESOLVED,
    RESOLVED,
    SUGGESTED,
    CONTRACTED,
    DISABLED;

    public static final String GROUP = "ISSUE_STATUS";
}
