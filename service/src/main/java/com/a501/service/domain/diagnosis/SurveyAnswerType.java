package com.a501.service.domain.diagnosis;

/** 설문 문항이 답을 받는 방식. {@code system_enums} 의 {@code SURVEY_ANSWER_TYPE} 묶음과 같은 값이다. */
public enum SurveyAnswerType {
    TEXT,
    SINGLE,
    MULTI;

    public static final String GROUP = "SURVEY_ANSWER_TYPE";

    public boolean isText() {
        return this == TEXT;
    }
}
