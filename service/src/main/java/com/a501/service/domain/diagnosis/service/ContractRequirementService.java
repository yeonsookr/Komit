package com.a501.service.domain.diagnosis.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.code.service.CodeQueryService;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.IssueCategory;
import com.a501.service.domain.diagnosis.IssueStatus;
import com.a501.service.domain.diagnosis.dto.DraftRequirements;
import com.a501.service.domain.diagnosis.entity.Diagnosis;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.entity.Issue;
import com.a501.service.domain.diagnosis.entity.QaCriterion;
import com.a501.service.domain.diagnosis.repository.DiagnosisRepository;
import com.a501.service.domain.diagnosis.repository.InspectionRepository;
import com.a501.service.domain.diagnosis.repository.IssueRepository;
import com.a501.service.domain.diagnosis.repository.QaCriterionRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계약서가 쓰는 요구사항과 확인 문장을 만든다. 계약 도메인이 진단 표를 직접 만지지 않도록 이 창구를 둔다.
 * (BACKEND_CONVENTIONS 7.1)
 *
 * <p><b>요청서는 계약서가 건드리지 않는다.</b> 여기서 만드는 것은 모두 계약용 표시를 달고, 요청서와 진단
 * 목록은 표시 없는 것만 읽는다. 계약서에서 바꾼 이름·설명·확인 문장이 요청서로 번지지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractRequirementService {
    private final DiagnosisRepository diagnosisRepository;
    private final InspectionRepository inspectionRepository;
    private final IssueRepository issueRepository;
    private final QaCriterionRepository qaCriterionRepository;
    private final CodeQueryService codeQueryService;

    /**
     * 계약서에서 새로 합의한 작업의 요구사항을 만든다. AI 초안(무료 상담에서 더 하자고 한 것)과 개발방
     * 재작성(사람이 추가한 것)이 함께 쓴다.
     *
     * <p>프로젝트 진단에서 검사가 있는 가장 최근 회차에 붙인다. 분류는 「기타」다 — 계약용 요구사항은
     * 요청서·진단 목록에 나오지 않아 분류를 쓰는 화면이 없고, 요청에 작업 종류를 가를 표시도 없다.
     *
     * @return 만든 요구사항 id
     * @throws BusinessException {@link DiagnosisErrorCode#INSPECTION_NOT_DONE} 프로젝트에 검사가 한 번도 없을 때
     */
    @Transactional
    public Integer createIssue(Integer projectId, String title, String detail) {
        Inspection inspection = latestInspection(projectId);
        return issueRepository.save(Issue.forContract(inspection.getId(), title, detail, etcCategoryId()))
                .getId();
    }

    /**
     * 요구사항에 계약서의 확인 문장을 붙인다. 요청서 문장과 따로 저장해, 의뢰인이 요청서를 고쳐도 계약의
     * 통과 조건이 바뀌지 않게 한다.
     *
     * @return 저장한 확인 문장 id. 보낸 순서대로다
     */
    @Transactional
    public List<Integer> writeCriteria(Integer issueId, List<String> testCases) {
        return testCases.stream()
                .map(testCase -> qaCriterionRepository.save(QaCriterion.forContract(issueId, testCase)).getId())
                .toList();
    }

    /**
     * 요청서에서 가져온 요구사항을 계약서에 들어간 것으로 표시한다.
     *
     * @throws BusinessException {@link DiagnosisErrorCode#ISSUE_NOT_FOUND} 없는 요구사항이 섞였을 때
     */
    @Transactional
    public void markContracted(Collection<Integer> issueIds) {
        Set<Integer> wanted = Set.copyOf(issueIds);
        List<Issue> issues = issueRepository.findAllByIdInAndDeletedFalse(wanted);
        if (issues.size() != wanted.size()) {
            throw new BusinessException(DiagnosisErrorCode.ISSUE_NOT_FOUND);
        }
        issues.forEach(Issue::putIntoContract);
    }

    /**
     * 프로젝트 요청서에 담긴 요구사항. 진단의 현재 회차에서 의뢰인이 고른 것과 이미 계약에 들어간 것이다.
     * 개발자는 요청서에 담긴 것만 볼 수 있어 계약서에도 이것만 넣을 수 있다. (R-REQ-05)
     *
     * <p>계약서에서 만든 요구사항은 요청서에 없어 여기 빠진다. 계약서를 다시 쓸 때 그것을 받을지는 부르는
     * 쪽이 그 계약에 이미 들어 있는지로 가린다.
     *
     * @return 요구사항 id. 진단이나 현재 회차 검사가 없으면 비어 있다
     */
    public Set<Integer> findRequestIssueIds(Integer projectId) {
        return diagnosisRepository
                .findByProjectIdAndDeletedFalse(projectId)
                .flatMap(diagnosis -> inspectionRepository.findByDiagnosisIdAndDiagnosisVersionAndDeletedFalse(
                        diagnosis.getId(), diagnosis.getVersion()))
                .map(inspection -> issueRepository
                        .findByInspectionIdAndDeletedFalseOrderByIdAsc(inspection.getId())
                        .stream()
                        .filter(issue -> Issue.REQUEST_STATUSES.contains(issue.getStatus()))
                        .map(Issue::getId)
                        .collect(Collectors.toSet()))
                .orElseGet(Set::of);
    }

    /**
     * 개발 계약 초안이 쓰는 요청서. 진단의 현재 회차에서 요청서에 담긴 요구사항과 그 확인 문장, 의뢰인이 고르지
     * 않은 요구사항이다. 계약서에서 만든 요구사항과 계약서 문장은 빠진다.
     *
     * <p>의뢰인이 직접 해결한 것은 고르지 않은 것이 아니라 끝난 것이라 어느 쪽에도 넣지 않는다.
     *
     * @return 요청서. 진단이나 현재 회차 검사가 없으면 둘 다 비어 있다
     */
    public DraftRequirements findDraftRequirements(Integer projectId) {
        List<Issue> issues = diagnosisRepository
                .findByProjectIdAndDeletedFalse(projectId)
                .flatMap(diagnosis -> inspectionRepository.findByDiagnosisIdAndDiagnosisVersionAndDeletedFalse(
                        diagnosis.getId(), diagnosis.getVersion()))
                .map(inspection -> issueRepository.findByInspectionIdAndDeletedFalseOrderByIdAsc(inspection.getId()))
                .orElseGet(List::of);
        List<Issue> requested = issues.stream()
                .filter(issue -> Issue.REQUEST_STATUSES.contains(issue.getStatus()))
                .toList();
        Map<Integer, List<String>> criteria = requested.isEmpty()
                ? Map.of()
                : qaCriterionRepository
                        .findByIssueIdInAndDeletedFalseOrderByIdAsc(requested.stream().map(Issue::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(
                                QaCriterion::getIssueId,
                                Collectors.mapping(QaCriterion::getTestCase, Collectors.toList())));
        return new DraftRequirements(
                requested.stream()
                        .map(issue -> new DraftRequirements.Requested(
                                issue.getId(),
                                issue.getTitle(),
                                issue.getDetail(),
                                criteria.getOrDefault(issue.getId(), List.of())))
                        .toList(),
                issues.stream()
                        .filter(issue -> IssueStatus.UNRESOLVED.name().equals(issue.getStatus()))
                        .map(issue -> new DraftRequirements.LeftOut(issue.getId(), issue.getTitle()))
                        .toList());
    }

    /**
     * 계약서를 다시 써서 빠진 계약서 문장을 지운다. 요청서 문장은 계약서가 지우지 않는다 — 섞여 오면
     * 부르는 쪽 잘못이라 멈춘다.
     */
    @Transactional
    public void dropCriteria(Collection<Integer> qaCriterionIds) {
        LocalDateTime now = LocalDateTime.now();
        qaCriterionRepository.findAllByIdInAndDeletedFalse(qaCriterionIds)
                .forEach(criterion -> criterion.dropFromContract(now));
    }

    private Inspection latestInspection(Integer projectId) {
        Diagnosis diagnosis = diagnosisRepository
                .findByProjectIdAndDeletedFalse(projectId)
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.INSPECTION_NOT_DONE));
        return inspectionRepository
                .findFirstByDiagnosisIdAndDeletedFalseOrderByDiagnosisVersionDesc(diagnosis.getId())
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.INSPECTION_NOT_DONE));
    }

    /** 「기타」는 시드가 넣는 코드다. 없으면 서버 설정이 잘못된 것이라 요청 탓으로 돌리지 않는다. */
    private Integer etcCategoryId() {
        String etc = IssueCategory.ETC.codeName();
        Integer id = codeQueryService.findCodeIdsByName(IssueCategory.GROUP, List.of(etc)).get(etc);
        if (id == null) {
            // handleBusiness 는 로그를 남기지 않는다. 여기서 안 남기면 500 이 이유 없이 나간다.
            log.error("요구사항 분류 코드에 {} 가 없다. 시드(data.sql)를 확인한다", etc);
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        return id;
    }
}
