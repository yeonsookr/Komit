package com.a501.service.domain.diagnosis.service;

import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.code.service.CodeQueryService;
import com.a501.service.domain.diagnosis.dto.response.IssueListResponse;
import com.a501.service.domain.diagnosis.dto.response.IssueResponse;
import com.a501.service.domain.diagnosis.dto.response.QaCriterionResponse;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.entity.Issue;
import com.a501.service.domain.diagnosis.entity.QaCriterion;
import com.a501.service.domain.diagnosis.repository.InspectionRepository;
import com.a501.service.domain.diagnosis.repository.IssueRepository;
import com.a501.service.domain.diagnosis.repository.QaCriterionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진단 회차에서 나온 결함을 읽는다. 진단서의 문제점 목록, 요구사항 선택, 요청서가 모두 이 목록을 그린다.
 *
 * <p>요청서는 따로 저장되는 문서가 아니라 이 요구사항들을 모아 부르는 이름이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueQueryService {
    private final InspectionRepository inspectionRepository;
    private final IssueRepository issueRepository;
    private final QaCriterionRepository qaCriterionRepository;
    private final CodeQueryService codeQueryService;

    public IssueListResponse getIssues(
            Integer diagnosisId,
            Short diagnosisVersion,
            String status,
            Boolean input,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Optional<Inspection> inspection =
                inspectionRepository.findByDiagnosisIdAndDiagnosisVersionAndDeletedFalse(
                        diagnosisId, diagnosisVersion);
        if (inspection.isEmpty()) {
            // 검사 전이다. 진단은 있으므로 빈 목록을 돌려준다.
            return IssueListResponse.of(Page.empty(pageable), page);
        }

        Page<Issue> found =
                issueRepository.findPage(inspection.get().getId(), status, input, pageable);
        return IssueListResponse.of(found.map(toResponse(found.getContent())), page);
    }

    private java.util.function.Function<Issue, IssueResponse> toResponse(List<Issue> issues) {
        Map<Integer, CodeResponse> categories = codeQueryService.findCodes(
                issues.stream().map(Issue::getCategoryCodeId).collect(Collectors.toSet()));
        Map<Integer, List<QaCriterion>> criteria = criteriaOf(issues);

        return issue -> new IssueResponse(
                issue.getId(),
                issue.getTitle(),
                issue.getDetail(),
                issue.getLocation(),
                issue.getInput(),
                categories.get(issue.getCategoryCodeId()),
                issue.getStatus(),
                issue.getSeverity(),
                issue.getFixedVersion() == null ? null : issue.getFixedVersion().intValue(),
                issue.getOriginIssueId(),
                criteria.getOrDefault(issue.getId(), List.of()).stream()
                        .map(QaCriterionResponse::from)
                        .toList());
    }

    private Map<Integer, List<QaCriterion>> criteriaOf(List<Issue> issues) {
        if (issues.isEmpty()) {
            return Map.of();
        }
        return qaCriterionRepository
                .findByIssueIdInAndDeletedFalseOrderByIdAsc(
                        issues.stream().map(Issue::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(QaCriterion::getIssueId));
    }
}
