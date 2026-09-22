package com.a501.service.domain.diagnosis.service;

import com.a501.service.domain.diagnosis.dto.IssueBrief;
import com.a501.service.domain.diagnosis.dto.RequestSummary;
import com.a501.service.domain.diagnosis.dto.response.QaCriterionResponse;
import com.a501.service.domain.diagnosis.entity.Diagnosis;
import com.a501.service.domain.diagnosis.entity.Issue;
import com.a501.service.domain.diagnosis.repository.DiagnosisRepository;
import com.a501.service.domain.diagnosis.repository.IssueRepository;
import com.a501.service.domain.diagnosis.repository.QaCriterionRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인이 요구사항과 확인 문장을 물어볼 때 쓰는 조회 창구.
 *
 * <p>계약서는 작업 이름·위치와 확인 문장을, 방은 요청서 요약을 보여 주는데 그 값들이 진단 쪽에
 * 있습니다. 묻는 쪽이 진단 엔티티를 직접 읽지 않도록 여기서 표시용 값만 넘깁니다.
 * (BACKEND_CONVENTIONS 7.1)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosisQueryService {
    private final DiagnosisRepository diagnosisRepository;
    private final IssueRepository issueRepository;
    private final QaCriterionRepository qaCriterionRepository;

    /**
     * 요구사항 식별자별 표시용 정보. 없거나 지워진 요구사항은 결과에 빠집니다.
     *
     * @param issueIds 요구사항 id 목록
     * @return 식별자 → 표시용 정보
     */
    public Map<Integer, IssueBrief> findBriefs(Collection<Integer> issueIds) {
        if (issueIds.isEmpty()) {
            return Map.of();
        }
        return issueRepository.findAllByIdInAndDeletedFalse(issueIds).stream()
                .collect(Collectors.toMap(Issue::getId, this::toBrief));
    }

    /**
     * 확인 문장 식별자별 내용. 없거나 지워진 문장은 결과에 빠집니다.
     *
     * @param qaCriterionIds 확인 문장 id 목록
     * @return 식별자 → 확인 문장
     */
    public Map<Integer, QaCriterionResponse> findQaCriteria(Collection<Integer> qaCriterionIds) {
        if (qaCriterionIds.isEmpty()) {
            return Map.of();
        }
        return qaCriterionRepository.findAllByIdInAndDeletedFalse(qaCriterionIds).stream()
                .map(QaCriterionResponse::from)
                .collect(Collectors.toMap(QaCriterionResponse::qaCriterionId, response -> response));
    }

    /**
     * 이 프로젝트의 요청서 요약. 아직 진단이 없으면 비어 있습니다.
     *
     * @param projectId 프로젝트 id
     * @return 요청서 요약
     */
    public Optional<RequestSummary> findRequestSummary(Integer projectId) {
        return diagnosisRepository.findByProjectIdAndDeletedFalse(projectId).map(this::toSummary);
    }

    private RequestSummary toSummary(Diagnosis diagnosis) {
        return new RequestSummary(
                diagnosis.getId(),
                issueRepository.countInRequest(diagnosis.getId(), diagnosis.getVersion()),
                diagnosis.getUpdatedAt());
    }

    private IssueBrief toBrief(Issue issue) {
        return new IssueBrief(issue.getTitle(), issue.getLocation());
    }
}
