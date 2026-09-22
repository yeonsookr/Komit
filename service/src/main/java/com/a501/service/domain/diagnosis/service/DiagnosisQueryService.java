package com.a501.service.domain.diagnosis.service;

import com.a501.service.domain.diagnosis.dto.DiagnosisSummary;
import com.a501.service.domain.diagnosis.entity.Diagnosis;
import com.a501.service.domain.diagnosis.repository.DiagnosisRepository;
import java.util.Collection;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 도메인이 진단 엔티티 없이 최근 진단 정보만 가져가는 창구입니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosisQueryService {
    private final DiagnosisRepository diagnosisRepository;

    public Map<Integer, DiagnosisSummary> findLatestByProjects(Collection<Integer> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }
        return diagnosisRepository.findByProjectIdInAndDeletedFalse(projectIds).stream()
                .collect(Collectors.toMap(
                        Diagnosis::getProjectId,
                        diagnosis -> new DiagnosisSummary(diagnosis.getId(), diagnosis.getCreatedAt()),
                        BinaryOperator.maxBy((left, right) -> left.diagnosedAt().compareTo(right.diagnosedAt()))));
    }
}
