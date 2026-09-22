package com.a501.service.domain.diagnosis.dto;

import java.time.LocalDateTime;

/**
 * 방 머리에 붙는 요청서 요약.
 *
 * <p><b>요청서는 방이 아니라 프로젝트에 달립니다.</b> 상담·컨설팅·개발 방이 모두 같은 요청서를
 * 보므로, 요청서를 고치면 열려 있는 모든 방의 머리가 함께 바뀝니다.
 *
 * @param diagnosisId 요청서의 근거가 된 진단
 * @param issueCount 요청서에 남아 있는 요구사항 수
 * @param updatedAt 요청서를 마지막으로 고친 시각
 */
public record RequestSummary(Integer diagnosisId, long issueCount, LocalDateTime updatedAt) {}
