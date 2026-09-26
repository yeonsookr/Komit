package com.a501.service.domain.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.code.service.CodeQueryService;
import com.a501.service.domain.diagnosis.IssueSeverity;
import com.a501.service.domain.diagnosis.dto.request.WriteRequirementsRequest.WriteQaCriterion;
import com.a501.service.domain.diagnosis.dto.request.WriteRequirementsRequest.WriteRequirement;
import com.a501.service.domain.diagnosis.dto.response.IssueListResponse;
import com.a501.service.domain.diagnosis.dto.response.IssueResponse;
import com.a501.service.domain.diagnosis.dto.response.QaCriterionResponse;
import com.a501.service.domain.diagnosis.entity.Diagnosis;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.entity.Issue;
import com.a501.service.domain.diagnosis.entity.QaCriterion;
import com.a501.service.domain.diagnosis.repository.DiagnosisRepository;
import com.a501.service.domain.diagnosis.repository.InspectionRepository;
import com.a501.service.domain.diagnosis.repository.IssueRepository;
import com.a501.service.domain.diagnosis.repository.QaCriterionRepository;
import com.a501.service.domain.project.service.ProjectService;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 계약서에서 만든 요구사항·확인 문장이 있는 채로 의뢰인이 요청서를 저장하고 읽는 흐름을 실제 PostgreSQL 18에서 본다.
 *
 * <p>요청서 저장은 그 검사의 요구사항을 모두 읽어, 보낸 목록에 없는 것은 뺀 것으로 처리한다. 계약용이 거기 섞이면
 * 계약에 들어간 것을 빼려 한 것으로 보고 409 로 막고, 계약서 확인 문장은 「요청서에서 지운 문장」이 된다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, RequirementService.class, IssueQueryService.class})
class RequirementContractSeparationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @MockitoBean private CodeQueryService codeQueryService;
    @MockitoBean private DiagnosisOwnership diagnosisOwnership;
    @MockitoBean private ProjectService projectService;

    @Autowired private RequirementService requirementService;
    @Autowired private IssueQueryService issueQueryService;
    @Autowired private DiagnosisRepository diagnosisRepository;
    @Autowired private InspectionRepository inspectionRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private QaCriterionRepository qaCriterionRepository;
    @Autowired private EntityManager entityManager;

    private Integer diagnosisId;
    private Issue suggested;
    private Issue contract;
    private QaCriterion requirementLine;
    private QaCriterion contractLine;
    private QaCriterion contractIssueLine;

    @BeforeEach
    void setUp() {
        diagnosisId = diagnosisRepository.saveAndFlush(Diagnosis.of(8)).getId();
        Integer inspectionId =
                inspectionRepository.saveAndFlush(Inspection.of(diagnosisId, (short) 1, 50, null)).getId();

        suggested = Issue.writtenBySelf(inspectionId, "관리자 API에 인증 추가", null, 71, "HIGH");
        suggested.putIntoRequirements();
        suggested = issueRepository.saveAndFlush(suggested);
        contract = issueRepository.saveAndFlush(Issue.forContract(inspectionId, "관리자 화면에 검색 추가", null, 74));

        requirementLine = qaCriterionRepository.saveAndFlush(QaCriterion.of(suggested.getId(), "로그인 화면이 뜬다"));
        contractLine = qaCriterionRepository.saveAndFlush(QaCriterion.forContract(suggested.getId(), "관리자만 들어간다"));
        contractIssueLine = qaCriterionRepository.saveAndFlush(QaCriterion.forContract(contract.getId(), "검색이 된다"));
    }

    @Test
    @DisplayName("계약용 요구사항이 있어도 의뢰인은 요청서를 저장할 수 있다")
    void clientCanSaveRequirementsBesideContractIssues() {
        IssueListResponse saved = requirementService.write(diagnosisId, (short) 1, List.of(keepSuggested()));

        assertThat(saved.content()).extracting(IssueResponse::issueId).containsExactly(suggested.getId());
    }

    @Test
    @DisplayName("요청서를 저장해도 계약용 요구사항과 계약서 확인 문장은 그대로 남는다")
    void savingRequirementsLeavesContractRowsAlone() {
        requirementService.write(diagnosisId, (short) 1, List.of(keepSuggested()));
        entityManager.flush();
        entityManager.clear();

        assertThat(issueRepository.findById(contract.getId())).get()
                .satisfies(issue -> {
                    assertThat(issue.getStatus()).isEqualTo("CONTRACTED");
                    assertThat(issue.isDeleted()).isFalse();
                });
        assertThat(qaCriterionRepository.findAllByIdInAndDeletedFalse(
                        List.of(contractLine.getId(), contractIssueLine.getId())))
                .hasSize(2);
    }

    @Test
    @DisplayName("요청서에서 문장을 지우면 요청서 문장만 지워진다")
    void droppingRequirementLineKeepsContractLine() {
        WriteRequirement withoutLines = new WriteRequirement(
                suggested.getId(), "관리자 API에 인증 추가", null, 71, IssueSeverity.HIGH, List.of());

        requirementService.write(diagnosisId, (short) 1, List.of(withoutLines));

        assertThat(qaCriterionRepository.findAllByIdInAndDeletedFalse(
                        List.of(requirementLine.getId(), contractLine.getId())))
                .extracting(QaCriterion::getId)
                .containsExactly(contractLine.getId());
    }

    @Test
    @DisplayName("요청서 화면에는 계약용 요구사항도 계약서 확인 문장도 나오지 않는다")
    void requirementScreenShowsNoContractRows() {
        IssueListResponse page = issueQueryService.getIssues(diagnosisId, (short) 1, null, null, true, 1, 20);

        assertThat(page.content()).extracting(IssueResponse::issueId).containsExactly(suggested.getId());
        assertThat(page.content().getFirst().qaCriteria())
                .extracting(QaCriterionResponse::testCase)
                .containsExactly("로그인 화면이 뜬다");
    }

    private WriteRequirement keepSuggested() {
        return new WriteRequirement(
                suggested.getId(),
                "관리자 API에 인증 추가",
                null,
                71,
                IssueSeverity.HIGH,
                List.of(new WriteQaCriterion(requirementLine.getId(), "로그인 화면이 뜬다")));
    }
}
