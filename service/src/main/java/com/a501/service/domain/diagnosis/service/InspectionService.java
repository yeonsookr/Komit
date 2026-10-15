package com.a501.service.domain.diagnosis.service;

import com.a501.service.domain.diagnosis.IssueCategory;
import com.a501.service.domain.diagnosis.dto.request.ScanResult;
import com.a501.service.domain.diagnosis.dto.response.InspectionResponse;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.entity.Issue;
import com.a501.service.domain.diagnosis.repository.InspectionRepository;
import com.a501.service.domain.diagnosis.repository.IssueRepository;
import com.a501.service.domain.code.service.CodeQueryService;
import com.a501.service.domain.project.service.ProjectService;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 올라온 검사 결과를 진단에 받아 적는다.
 *
 * <p>검사는 의뢰인 컴퓨터에서 끝났고 소스 코드는 오지 않는다. 서버가 하는 일은 결과를 읽어 결함과 마지막 설문 문항으로 옮기는 것이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionService {
    private final InspectionRepository inspectionRepository;
    private final IssueRepository issueRepository;
    private final SurveyService surveyService;
    private final CodeQueryService codeQueryService;
    private final ProjectService projectService;

    @Transactional
    public InspectionResponse record(
            Integer diagnosisId, Integer projectId, Short diagnosisVersion, ScanResult result) {
        Inspection inspection = inspectionRepository.save(Inspection.of(
                diagnosisId,
                diagnosisVersion,
                ReadinessScore.of(result.issues()),
                result.scannedAt() == null
                        ? null
                        : result.scannedAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()));

        List<Issue> saved = saveIssues(inspection.getId(), result.issues());
        carryOverFromPreviousVersion(diagnosisId, diagnosisVersion, saved);
        surveyService.replaceScanQuestions(diagnosisId, diagnosisVersion, result.questions());
        projectService.replaceTechCategories(projectId, result.techCategories());

        return InspectionResponse.from(inspection);
    }

    /**
     * 의뢰인이 적은 증상과 이어지는 결함을 앞에 둔다. 진단서가 그것을 맨 위에 따로 보여주는데, 목록이 길면 뒤쪽이 한 쪽에 실리지 않아 화면에서 사라진다. (R-SCAN-08)
     */
    private List<Issue> saveIssues(Integer inspectionId, List<ScanResult.Issue> issues) {
        Map<String, Integer> categoryIds = categoryIds(issues);
        Integer fallback = categoryIds.get(IssueCategory.ETC.codeName());

        return issues.stream()
                .sorted(Comparator.comparing(issue -> !Boolean.TRUE.equals(issue.isInput())))
                .map(issue -> issueRepository.save(Issue.of(
                        inspectionId,
                        issue.title(),
                        issue.detail(),
                        issue.location(),
                        categoryIds.getOrDefault(
                                IssueCategory.of(issue.category()).codeName(), fallback),
                        issue.severity(),
                        Boolean.TRUE.equals(issue.isInput()),
                        issue.fingerprint())))
                .toList();
    }

    private Map<String, Integer> categoryIds(List<ScanResult.Issue> issues) {
        Set<String> names = issues.stream()
                .map(issue -> IssueCategory.of(issue.category()).codeName())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        names.add(IssueCategory.ETC.codeName());
        return codeQueryService.findCodeIdsByName(IssueCategory.GROUP, names);
    }

    /**
     * 지난 회차와 견준다. 같은 지문이 이번에도 있으면 이어진 결함으로 묶고, 이번에 사라졌으면 지난 회차 행에 사라진 회차를 적는다. 지난 기록은 지우지 않는다. (R-SCAN-05)
     */
    private void carryOverFromPreviousVersion(
            Integer diagnosisId, Short diagnosisVersion, List<Issue> current) {
        if (diagnosisVersion <= 1) {
            return;
        }
        Optional<Inspection> previous = inspectionRepository
                .findByDiagnosisIdAndDiagnosisVersionAndDeletedFalse(
                        diagnosisId, (short) (diagnosisVersion - 1));
        if (previous.isEmpty()) {
            return;
        }

        List<Issue> before = issueRepository.findByInspectionIdAndDeletedFalseOrderByIdAsc(
                previous.get().getId());
        Map<String, Issue> byFingerprint = before.stream()
                .filter(issue -> issue.getFingerprint() != null)
                .collect(Collectors.toMap(
                        Issue::getFingerprint, issue -> issue, (first, ignored) -> first));

        Set<String> stillThere = current.stream()
                .map(Issue::getFingerprint)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        for (Issue issue : current) {
            Issue origin = byFingerprint.get(issue.getFingerprint());
            if (origin != null) {
                issue.continueFrom(
                        origin.getOriginIssueId() == null ? origin.getId() : origin.getOriginIssueId());
            }
        }
        before.stream()
                .filter(issue -> issue.getFixedVersion() == null)
                .filter(issue -> !stillThere.contains(issue.getFingerprint()))
                .forEach(issue -> issue.markFixedAt(diagnosisVersion));
    }
}
