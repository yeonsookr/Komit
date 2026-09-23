package com.a501.service.domain.diagnosis.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 추천을 만들 때 쓰는 진단 쪽 입력. 다른 도메인에 엔티티를 넘기지 않기 위한 경계다. (BACKEND_CONVENTIONS 7.1)
 *
 * @param techCodeIds 검사에서 확인한 프로젝트 기술. 점수의 기술 항이 이것과 개발자를 견준다
 * @param snapshot    당시 입력을 그대로 되살리기 위해 회차에 저장하는 값
 */
public record DiagnosisMatchInput(
        Integer diagnosisId,
        Short diagnosisVersion,
        List<Integer> issueIds,
        Set<Integer> techCodeIds,
        Map<String, Object> snapshot) {}
