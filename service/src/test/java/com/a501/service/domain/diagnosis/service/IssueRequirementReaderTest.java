package com.a501.service.domain.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.entity.Diagnosis;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.entity.Issue;
import com.a501.service.domain.diagnosis.repository.InspectionRepository;
import com.a501.service.domain.diagnosis.repository.IssueRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class IssueRequirementReaderTest {
    private static final Integer ISSUE_ID = 312;
    private static final Integer INSPECTION_ID = 55;
    private static final Integer DIAGNOSIS_ID = 7;
    private static final Integer USER_ID = 12;
    private static final String TITLE = "관리자 화면에 인증이 없다";

    private IssueRepository issueRepository;
    private InspectionRepository inspectionRepository;
    private DiagnosisOwnership diagnosisOwnership;
    private IssueRequirementReader reader;

    @BeforeEach
    void setUp() {
        issueRepository = mock(IssueRepository.class);
        inspectionRepository = mock(InspectionRepository.class);
        diagnosisOwnership = mock(DiagnosisOwnership.class);
        reader = new IssueRequirementReader(issueRepository, inspectionRepository, diagnosisOwnership);
    }

    @Test
    @DisplayName("제목과 내용을 함께 넘긴다")
    void joinsTitleAndDetail() {
        givenOwnedIssue("/admin 경로에 인증 검사가 없다.");

        assertThat(reader.read(ISSUE_ID, USER_ID))
                .contains(TITLE)
                .contains("/admin 경로에 인증 검사가 없다.");
    }

    @Test
    @DisplayName("내용이 비어 있으면 제목만 넘긴다")
    void sendsTitleOnlyWhenDetailIsBlank() {
        givenOwnedIssue("   ");

        assertThat(reader.read(ISSUE_ID, USER_ID)).isEqualTo(TITLE);
    }

    @Test
    @DisplayName("내용이 없어도 제목만 넘긴다")
    void sendsTitleOnlyWhenDetailIsNull() {
        givenOwnedIssue(null);

        assertThat(reader.read(ISSUE_ID, USER_ID)).isEqualTo(TITLE);
    }

    @Test
    @DisplayName("없는 요구사항은 404다")
    void missingIssueIsNotFound() {
        when(issueRepository.findByIdAndDeletedFalse(ISSUE_ID)).thenReturn(Optional.empty());

        assertNotFound();
    }

    @Test
    @DisplayName("검사가 사라졌으면 404다")
    void missingInspectionIsNotFound() {
        givenOwnedIssue("내용");
        when(inspectionRepository.findByIdAndDeletedFalse(INSPECTION_ID)).thenReturn(Optional.empty());

        assertNotFound();
    }

    @Test
    @DisplayName("남의 진단이면 404다. 있다는 것도 알리지 않는다")
    void othersDiagnosisIsNotFound() {
        givenOwnedIssue("내용");
        when(diagnosisOwnership.findOwned(DIAGNOSIS_ID, USER_ID)).thenReturn(Optional.empty());

        assertNotFound();
    }

    private void assertNotFound() {
        assertThatThrownBy(() -> reader.read(ISSUE_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.ISSUE_NOT_FOUND);
    }

    private void givenOwnedIssue(String detail) {
        Issue issue = BeanUtils.instantiateClass(Issue.class);
        ReflectionTestUtils.setField(issue, "id", ISSUE_ID);
        ReflectionTestUtils.setField(issue, "inspectionId", INSPECTION_ID);
        ReflectionTestUtils.setField(issue, "title", TITLE);
        ReflectionTestUtils.setField(issue, "detail", detail);

        Inspection inspection = BeanUtils.instantiateClass(Inspection.class);
        ReflectionTestUtils.setField(inspection, "id", INSPECTION_ID);
        ReflectionTestUtils.setField(inspection, "diagnosisId", DIAGNOSIS_ID);

        when(issueRepository.findByIdAndDeletedFalse(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(inspectionRepository.findByIdAndDeletedFalse(INSPECTION_ID))
                .thenReturn(Optional.of(inspection));
        when(diagnosisOwnership.findOwned(DIAGNOSIS_ID, USER_ID))
                .thenReturn(Optional.of(BeanUtils.instantiateClass(Diagnosis.class)));
    }
}
