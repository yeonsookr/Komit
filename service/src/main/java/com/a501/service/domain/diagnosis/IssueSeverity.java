package com.a501.service.domain.diagnosis;

/** 결함 위험도. {@code system_enums} 의 {@code ISSUE_SEVERITY} 묶음과 같은 값이다. (R-SCAN-09) */
public enum IssueSeverity {
    LOW,
    MEDIUM,
    HIGH;

    public static final String GROUP = "ISSUE_SEVERITY";
}
