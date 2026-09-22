package com.a501.service.domain.diagnosis.dto;

import java.time.LocalDateTime;

/** 마이페이지 프로젝트 한 줄에 붙이는 최근 진단 정보. */
public record DiagnosisSummary(Integer diagnosisId, LocalDateTime diagnosedAt) {
}
