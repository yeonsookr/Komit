package com.a501.service.domain.diagnosis.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.dto.request.CreateIssueRequest;
import com.a501.service.domain.diagnosis.dto.request.WriteRequirementsRequest.WriteQaCriterion;
import com.a501.service.domain.diagnosis.dto.request.WriteRequirementsRequest.WriteRequirement;
import com.a501.service.domain.diagnosis.dto.response.IssueListResponse;
import com.a501.service.domain.diagnosis.dto.response.IssueResponse;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.entity.Issue;
import com.a501.service.domain.diagnosis.entity.QaCriterion;
import com.a501.service.domain.diagnosis.repository.InspectionRepository;
import com.a501.service.domain.diagnosis.repository.IssueRepository;
import com.a501.service.domain.diagnosis.repository.QaCriterionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요청서를 다듬는다.
 *
 * <p>요청서는 따로 저장되는 문서가 아니라 이 회차의 요구사항을 모아 부르는 이름이다. 저장하면 그 자리에서 최신 내용이 되고 따로 보내는 절차가 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequirementService {
    private final InspectionRepository inspectionRepository;
    private final IssueRepository issueRepository;
    private final QaCriterionRepository qaCriterionRepository;
    private final IssueQueryService issueQueryService;

    /** 검사가 찾지 못했지만 고치고 싶은 것을 직접 적어 넣는다. 검사에서 나온 결함과 같은 목록에 미해결로 들어간다. */
    @Transactional
    public IssueResponse addIssue(
            Integer diagnosisId, Short diagnosisVersion, CreateIssueRequest request) {
        Inspection inspection = requireInspection(diagnosisId, diagnosisVersion);
        Issue saved = issueRepository.save(Issue.writtenBySelf(
                inspection.getId(),
                request.title(),
                request.detail(),
                request.categoryCodeId(),
                request.severity().name()));
        return issueQueryService.toResponses(List.of(saved)).getFirst();
    }

    /**
     * 요청서를 통째로 받아 보낸 내용으로 맞춘다. 보낸 목록에 없는 기존 요구사항은 뺀 것으로 보고, 요구사항 안에서 보낸 목록에 없는 확인 문장도 지운 것으로 본다.
     *
     * @throws BusinessException {@link DiagnosisErrorCode#ISSUE_STATUS_CONFLICT} 계약에 들어간 요구사항을 빼려 할 때
     */
    @Transactional
    public IssueListResponse write(
            Integer diagnosisId, Short diagnosisVersion, List<WriteRequirement> requirements) {
        Inspection inspection = requireInspection(diagnosisId, diagnosisVersion);
        Map<Integer, Issue> existing = issueRepository
                .findByInspectionIdAndDeletedFalseOrderByIdAsc(inspection.getId())
                .stream()
                .collect(Collectors.toMap(Issue::getId, issue -> issue));

        LocalDateTime now = LocalDateTime.now();
        List<Issue> kept = new ArrayList<>();
        for (WriteRequirement requirement : requirements) {
            kept.add(upsert(inspection.getId(), requirement, existing, now));
        }

        Set<Integer> keptIds = kept.stream().map(Issue::getId).collect(Collectors.toSet());
        for (Issue issue : existing.values()) {
            if (keptIds.contains(issue.getId())) {
                continue;
            }
            // 계약서에 적힌 작업이 사라지면 안 된다. 그런 줄을 빼고 보내면 막는다.
            if (issue.isContracted()) {
                throw new BusinessException(DiagnosisErrorCode.ISSUE_STATUS_CONFLICT);
            }
            issue.dropFromRequirements(now);
        }

        List<IssueResponse> content = issueQueryService.toResponses(kept);
        return new IssueListResponse(content, 1, content.size(), content.size(), 1);
    }

    private Issue upsert(
            Integer inspectionId,
            WriteRequirement requirement,
            Map<Integer, Issue> existing,
            LocalDateTime now) {
        Issue issue = requirement.issueId() == null ? null : existing.get(requirement.issueId());
        if (issue == null) {
            issue = issueRepository.save(Issue.writtenBySelf(
                    inspectionId,
                    requirement.title(),
                    requirement.detail(),
                    requirement.categoryCodeId(),
                    requirement.severity().name()));
        } else {
            issue.rewrite(
                    requirement.title(),
                    requirement.detail(),
                    requirement.categoryCodeId(),
                    requirement.severity().name());
        }
        writeCriteria(issue.getId(), requirement.qaCriteria(), now);
        return issue;
    }

    private void writeCriteria(Integer issueId, List<WriteQaCriterion> wanted, LocalDateTime now) {
        Map<Integer, QaCriterion> existing = new HashMap<>();
        qaCriterionRepository
                .findByIssueIdInAndDeletedFalseOrderByIdAsc(List.of(issueId))
                .forEach(criterion -> existing.put(criterion.getId(), criterion));

        Set<Integer> keptIds = new java.util.HashSet<>();
        for (WriteQaCriterion criterion : wanted) {
            QaCriterion kept = criterion.qaCriterionId() == null
                    ? null
                    : existing.get(criterion.qaCriterionId());
            if (kept == null) {
                kept = qaCriterionRepository.save(QaCriterion.of(issueId, criterion.testCase()));
            } else {
                kept.rewrite(criterion.testCase());
            }
            keptIds.add(kept.getId());
        }
        existing.values().stream()
                .filter(criterion -> !keptIds.contains(criterion.getId()))
                .forEach(criterion -> criterion.dropFromRequirements(now));
    }

    private Inspection requireInspection(Integer diagnosisId, Short diagnosisVersion) {
        return inspectionRepository
                .findByDiagnosisIdAndDiagnosisVersionAndDeletedFalse(diagnosisId, diagnosisVersion)
                // 검사 전에는 요구사항을 붙일 검사가 없다. 화면은 검사 뒤에만 이 자리에 온다.
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.INSPECTION_NOT_DONE));
    }
}
