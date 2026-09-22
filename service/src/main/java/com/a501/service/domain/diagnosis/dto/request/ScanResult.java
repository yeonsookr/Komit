package com.a501.service.domain.diagnosis.dto.request;

import com.a501.service.domain.diagnosis.SurveyAnswerType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 의뢰인이 자기 AI 도구로 돌려 만든 검사 결과. 결과 파일 안 JSON 블록의 모양이다.
 *
 * <p>담을 곳이 없는 값은 받지 않는다. 코드 조각·보안 세부 분류·문항의 근거·검사 범위·의존성 검사 출력은 ERD 에 자리가 없고 화면도 쓰지 않는다. 필요해지면 ERD 를 먼저 늘린다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanResult(
        Integer diagnosisId,
        String scanVersion,
        OffsetDateTime scannedAt,
        List<String> techCategories,
        List<Issue> issues,
        List<Question> questions) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Issue(
            String title,
            String detail,
            String location,
            String category,
            String severity,
            Boolean isInput,
            String fingerprint) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Question(String content, SurveyAnswerType answerType, List<String> options) {}
}
