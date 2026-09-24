package com.a501.service.domain.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.client.QaSuggestionClient;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionListResponse;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionResponse;
import com.a501.service.domain.diagnosis.entity.Diagnosis;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.entity.Issue;
import com.a501.service.domain.diagnosis.repository.InspectionRepository;
import com.a501.service.domain.diagnosis.repository.IssueRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class QaSuggestionServiceTest {
    private static final Integer ISSUE_ID = 312;
    private static final Integer INSPECTION_ID = 55;
    private static final Integer DIAGNOSIS_ID = 7;
    private static final Integer USER_ID = 12;

    private IssueRepository issueRepository;
    private InspectionRepository inspectionRepository;
    private DiagnosisOwnership diagnosisOwnership;
    private QaSuggestionClient qaSuggestionClient;
    private QaSuggestionService service;

    @BeforeEach
    void setUp() {
        issueRepository = mock(IssueRepository.class);
        inspectionRepository = mock(InspectionRepository.class);
        diagnosisOwnership = mock(DiagnosisOwnership.class);
        qaSuggestionClient = mock(QaSuggestionClient.class);
        service = new QaSuggestionService(
                issueRepository, inspectionRepository, diagnosisOwnership, qaSuggestionClient);
    }

    @Test
    @DisplayName("후보 세 건을 목록 형식으로 감싸 돌려준다")
    void wrapsSuggestionsIntoListResponse() {
        givenOwnedIssue("관리자 화면에 인증이 없다", "/admin 경로에 인증 검사가 없다.");
        when(qaSuggestionClient.suggest(any()))
                .thenReturn(List.of(
                        new QaSuggestionResponse("첫째 문장"),
                        new QaSuggestionResponse("둘째 문장"),
                        new QaSuggestionResponse("셋째 문장")));

        QaSuggestionListResponse response = service.suggest(ISSUE_ID, USER_ID);

        assertThat(response.content()).hasSize(3);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("제목과 내용을 함께 넘긴다")
    void sendsTitleAndDetailTogether() {
        givenOwnedIssue("관리자 화면에 인증이 없다", "/admin 경로에 인증 검사가 없다.");
        when(qaSuggestionClient.suggest(any())).thenReturn(List.of(new QaSuggestionResponse("문장")));

        service.suggest(ISSUE_ID, USER_ID);

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(qaSuggestionClient).suggest(sent.capture());
        assertThat(sent.getValue()).contains("관리자 화면에 인증이 없다").contains("/admin 경로에 인증 검사가 없다.");
    }

    @Test
    @DisplayName("내용이 없으면 제목만 넘긴다")
    void sendsTitleOnlyWhenDetailIsBlank() {
        givenOwnedIssue("관리자 화면에 인증이 없다", "  ");
        when(qaSuggestionClient.suggest(any())).thenReturn(List.of(new QaSuggestionResponse("문장")));

        service.suggest(ISSUE_ID, USER_ID);

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(qaSuggestionClient).suggest(sent.capture());
        assertThat(sent.getValue()).isEqualTo("관리자 화면에 인증이 없다");
    }

    @Test
    @DisplayName("없는 요구사항이면 AI를 부르지 않고 404다")
    void missingIssueNeverCallsAi() {
        when(issueRepository.findByIdAndDeletedFalse(ISSUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suggest(ISSUE_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.ISSUE_NOT_FOUND);
        verify(qaSuggestionClient, never()).suggest(any());
    }

    @Test
    @DisplayName("남의 진단이면 AI를 부르지 않고 404다")
    void othersDiagnosisNeverCallsAi() {
        givenOwnedIssue("제목", "내용");
        when(diagnosisOwnership.findOwned(DIAGNOSIS_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suggest(ISSUE_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.ISSUE_NOT_FOUND);
        verify(qaSuggestionClient, never()).suggest(any());
    }

    private void givenOwnedIssue(String title, String detail) {
        Issue issue = BeanUtils.instantiateClass(Issue.class);
        ReflectionTestUtils.setField(issue, "id", ISSUE_ID);
        ReflectionTestUtils.setField(issue, "inspectionId", INSPECTION_ID);
        ReflectionTestUtils.setField(issue, "title", title);
        ReflectionTestUtils.setField(issue, "detail", detail);

        Inspection inspection = BeanUtils.instantiateClass(Inspection.class);
        ReflectionTestUtils.setField(inspection, "id", INSPECTION_ID);
        ReflectionTestUtils.setField(inspection, "diagnosisId", DIAGNOSIS_ID);

        when(issueRepository.findByIdAndDeletedFalse(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(inspectionRepository.findByIdAndDeletedFalse(INSPECTION_ID)).thenReturn(Optional.of(inspection));
        when(diagnosisOwnership.findOwned(DIAGNOSIS_ID, USER_ID)).thenReturn(Optional.of(BeanUtils.instantiateClass(Diagnosis.class)));
    }
}
