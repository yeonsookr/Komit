package com.a501.service.domain.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.code.service.CodeQueryService;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.IssueCategory;
import com.a501.service.domain.diagnosis.entity.Diagnosis;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.entity.Issue;
import com.a501.service.domain.diagnosis.entity.QaCriterion;
import com.a501.service.domain.diagnosis.repository.DiagnosisRepository;
import com.a501.service.domain.diagnosis.repository.InspectionRepository;
import com.a501.service.domain.diagnosis.repository.IssueRepository;
import com.a501.service.domain.diagnosis.repository.QaCriterionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 계약서가 쓰는 요구사항과 확인 문장이 계약용 표시를 달고, 요청서 것과 섞이지 않게 만들어지는지 본다.
 */
class ContractRequirementServiceTest {
    private static final Integer PROJECT_ID = 8;
    private static final Integer DIAGNOSIS_ID = 7;
    private static final Integer INSPECTION_ID = 55;
    private static final Integer ETC_CATEGORY_ID = 74;

    private DiagnosisRepository diagnosisRepository;
    private InspectionRepository inspectionRepository;
    private IssueRepository issueRepository;
    private QaCriterionRepository qaCriterionRepository;
    private CodeQueryService codeQueryService;
    private ContractRequirementService service;

    @BeforeEach
    void setUp() {
        diagnosisRepository = mock(DiagnosisRepository.class);
        inspectionRepository = mock(InspectionRepository.class);
        issueRepository = mock(IssueRepository.class);
        qaCriterionRepository = mock(QaCriterionRepository.class);
        codeQueryService = mock(CodeQueryService.class);
        service = new ContractRequirementService(
                diagnosisRepository, inspectionRepository, issueRepository, qaCriterionRepository, codeQueryService);

        Diagnosis diagnosis = Diagnosis.of(PROJECT_ID);
        ReflectionTestUtils.setField(diagnosis, "id", DIAGNOSIS_ID);
        when(diagnosisRepository.findByProjectIdAndDeletedFalse(PROJECT_ID)).thenReturn(Optional.of(diagnosis));
        Inspection inspection = Inspection.of(DIAGNOSIS_ID, (short) 2, 40, null);
        ReflectionTestUtils.setField(inspection, "id", INSPECTION_ID);
        when(inspectionRepository.findFirstByDiagnosisIdAndDeletedFalseOrderByDiagnosisVersionDesc(DIAGNOSIS_ID))
                .thenReturn(Optional.of(inspection));
        when(codeQueryService.findCodeIdsByName(eq(IssueCategory.GROUP), anyCollection()))
                .thenReturn(Map.of(IssueCategory.ETC.codeName(), ETC_CATEGORY_ID));
        AtomicInteger ids = new AtomicInteger(900);
        when(issueRepository.save(any(Issue.class))).thenAnswer(call -> {
            Issue issue = call.getArgument(0);
            ReflectionTestUtils.setField(issue, "id", ids.incrementAndGet());
            return issue;
        });
        when(qaCriterionRepository.save(any(QaCriterion.class))).thenAnswer(call -> {
            QaCriterion criterion = call.getArgument(0);
            ReflectionTestUtils.setField(criterion, "id", ids.incrementAndGet());
            return criterion;
        });
    }

    @Test
    @DisplayName("새 작업의 요구사항은 계약용으로, 처음부터 계약 상태로 만든다")
    void createsContractIssue() {
        Integer id = service.createIssue(PROJECT_ID, "관리자 화면에 검색 추가", "회원을 찾을 수 있다");

        Issue saved = savedIssue();
        assertThat(id).isEqualTo(saved.getId());
        assertThat(saved.isContract()).isTrue();
        assertThat(saved.getStatus()).isEqualTo("CONTRACTED");
        assertThat(saved.getTitle()).isEqualTo("관리자 화면에 검색 추가");
        assertThat(saved.getDetail()).isEqualTo("회원을 찾을 수 있다");
    }

    @Test
    @DisplayName("진단이 찾은 것이 아니라 위치·지문이 없고 증상과도 잇지 않는다")
    void contractIssueHasNoScanTraces() {
        service.createIssue(PROJECT_ID, "관리자 화면에 검색 추가", null);

        Issue saved = savedIssue();
        assertThat(saved.getLocation()).isNull();
        assertThat(saved.getFingerprint()).isNull();
        assertThat(saved.getInput()).isFalse();
    }

    @Test
    @DisplayName("분류는 「기타」, 심각도는 보통으로 넣는다")
    void fillsCategoryAndSeverity() {
        service.createIssue(PROJECT_ID, "관리자 화면에 검색 추가", null);

        Issue saved = savedIssue();
        assertThat(saved.getCategoryCodeId()).isEqualTo(ETC_CATEGORY_ID);
        assertThat(saved.getSeverity()).isEqualTo("MEDIUM");
        assertThat(saved.getSeverityGroup()).isEqualTo("ISSUE_SEVERITY");
    }

    @Test
    @DisplayName("검사가 있는 가장 최근 회차에 붙인다")
    void attachesToLatestInspection() {
        service.createIssue(PROJECT_ID, "관리자 화면에 검색 추가", null);

        assertThat(savedIssue().getInspectionId()).isEqualTo(INSPECTION_ID);
    }

    @Test
    @DisplayName("프로젝트에 검사가 한 번도 없으면 만들지 않는다")
    void rejectsWhenNoInspection() {
        when(inspectionRepository.findFirstByDiagnosisIdAndDeletedFalseOrderByDiagnosisVersionDesc(DIAGNOSIS_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createIssue(PROJECT_ID, "관리자 화면에 검색 추가", null))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(DiagnosisErrorCode.INSPECTION_NOT_DONE));
    }

    @Test
    @DisplayName("프로젝트에 진단이 없어도 만들지 않는다")
    void rejectsWhenNoDiagnosis() {
        when(diagnosisRepository.findByProjectIdAndDeletedFalse(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createIssue(PROJECT_ID, "관리자 화면에 검색 추가", null))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(DiagnosisErrorCode.INSPECTION_NOT_DONE));
    }

    @Test
    @DisplayName("「기타」 코드가 시드에 없으면 요청 탓이 아니라 서버 오류다")
    void missingEtcCodeIsServerError() {
        when(codeQueryService.findCodeIdsByName(eq(IssueCategory.GROUP), anyCollection())).thenReturn(Map.of());

        assertThatThrownBy(() -> service.createIssue(PROJECT_ID, "관리자 화면에 검색 추가", null))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR));
    }

    @Test
    @DisplayName("확인 문장은 계약용으로 보낸 순서대로 저장한다")
    void writesContractCriteriaInOrder() {
        List<Integer> ids = service.writeCriteria(312, List.of("로그인 화면이 뜬다", "관리자만 들어간다"));

        ArgumentCaptor<QaCriterion> saved = ArgumentCaptor.forClass(QaCriterion.class);
        verify(qaCriterionRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(QaCriterion::getTestCase)
                .containsExactly("로그인 화면이 뜬다", "관리자만 들어간다");
        assertThat(saved.getAllValues()).allSatisfy(criterion -> {
            assertThat(criterion.isContract()).isTrue();
            assertThat(criterion.getIssueId()).isEqualTo(312);
        });
        assertThat(ids).containsExactly(
                saved.getAllValues().get(0).getId(), saved.getAllValues().get(1).getId());
    }

    @Test
    @DisplayName("요청서에서 가져온 요구사항을 계약 상태로 바꾼다")
    void marksExistingIssuesContracted() {
        Issue first = Issue.writtenBySelf(INSPECTION_ID, "관리자 API에 인증 추가", null, ETC_CATEGORY_ID, "HIGH");
        Issue second = Issue.writtenBySelf(INSPECTION_ID, "결제 간헐 실패 수정", null, ETC_CATEGORY_ID, "HIGH");
        first.putIntoRequirements();
        second.putIntoRequirements();
        when(issueRepository.findAllByIdInAndDeletedFalse(any())).thenReturn(List.of(first, second));

        service.markContracted(List.of(312, 311));

        assertThat(List.of(first, second)).allSatisfy(issue -> {
            assertThat(issue.getStatus()).isEqualTo("CONTRACTED");
            // 요청서에서 온 요구사항이다. 계약용 표시는 달지 않는다.
            assertThat(issue.isContract()).isFalse();
        });
    }

    @Test
    @DisplayName("없는 요구사항이 섞이면 거부한다")
    void rejectsMissingIssue() {
        Issue found = Issue.writtenBySelf(INSPECTION_ID, "관리자 API에 인증 추가", null, ETC_CATEGORY_ID, "HIGH");
        when(issueRepository.findAllByIdInAndDeletedFalse(any())).thenReturn(List.of(found));

        assertThatThrownBy(() -> service.markContracted(List.of(312, 404)))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(DiagnosisErrorCode.ISSUE_NOT_FOUND));
        assertThat(found.getStatus()).isEqualTo("UNRESOLVED");
    }

    @Test
    @DisplayName("요청서에 담긴 요구사항은 현재 회차에서 고른 것과 계약에 들어간 것이다")
    void findsRequestIssuesOfCurrentRound() {
        Inspection current = Inspection.of(DIAGNOSIS_ID, (short) 1, 50, null);
        ReflectionTestUtils.setField(current, "id", 60);
        when(inspectionRepository.findByDiagnosisIdAndDiagnosisVersionAndDeletedFalse(DIAGNOSIS_ID, (short) 1))
                .thenReturn(Optional.of(current));
        Issue suggested = issue(311, "SUGGESTED");
        Issue contracted = issue(312, "CONTRACTED");
        Issue unresolved = issue(313, "UNRESOLVED");
        Issue resolved = issue(314, "RESOLVED");
        when(issueRepository.findByInspectionIdAndDeletedFalseOrderByIdAsc(60))
                .thenReturn(List.of(suggested, contracted, unresolved, resolved));

        assertThat(service.findRequestIssueIds(PROJECT_ID)).containsExactlyInAnyOrder(311, 312);
    }

    @Test
    @DisplayName("현재 회차에 검사가 없으면 요청서도 비어 있다")
    void emptyRequestWithoutCurrentInspection() {
        when(inspectionRepository.findByDiagnosisIdAndDiagnosisVersionAndDeletedFalse(DIAGNOSIS_ID, (short) 1))
                .thenReturn(Optional.empty());

        assertThat(service.findRequestIssueIds(PROJECT_ID)).isEmpty();
    }

    @Test
    @DisplayName("계약서 문장을 지운다")
    void dropsContractCriteria() {
        QaCriterion line = QaCriterion.forContract(312, "관리자만 들어간다");
        when(qaCriterionRepository.findAllByIdInAndDeletedFalse(List.of(5001))).thenReturn(List.of(line));

        service.dropCriteria(List.of(5001));

        assertThat(line.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("요청서 문장은 계약서가 지우지 않는다")
    void refusesToDropRequirementLine() {
        QaCriterion line = QaCriterion.of(312, "로그인 화면이 뜬다");
        when(qaCriterionRepository.findAllByIdInAndDeletedFalse(List.of(5001))).thenReturn(List.of(line));

        assertThatThrownBy(() -> service.dropCriteria(List.of(5001))).isInstanceOf(IllegalStateException.class);
        assertThat(line.isDeleted()).isFalse();
    }

    private Issue issue(int id, String status) {
        Issue issue = Issue.writtenBySelf(60, "요구사항 " + id, null, ETC_CATEGORY_ID, "HIGH");
        ReflectionTestUtils.setField(issue, "id", id);
        ReflectionTestUtils.setField(issue, "status", status);
        return issue;
    }

    private Issue savedIssue() {
        ArgumentCaptor<Issue> saved = ArgumentCaptor.forClass(Issue.class);
        verify(issueRepository).save(saved.capture());
        return saved.getValue();
    }
}
