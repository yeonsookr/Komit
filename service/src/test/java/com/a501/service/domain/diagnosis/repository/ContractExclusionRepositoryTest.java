package com.a501.service.domain.diagnosis.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.diagnosis.entity.Diagnosis;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.entity.Issue;
import com.a501.service.domain.diagnosis.entity.QaCriterion;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 요청서와 진단 목록이 쓰는 조회가 계약서에서 만든 요구사항·확인 문장을 빼는지 실제 PostgreSQL 18에서 본다.
 * 계약서 화면이 쓰는 식별자 조회는 반대로 계약용을 그대로 돌려줘야 한다.
 * (BACKEND_CONVENTIONS 19 Repository 통합 테스트)
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class ContractExclusionRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Autowired private DiagnosisRepository diagnosisRepository;
    @Autowired private InspectionRepository inspectionRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private QaCriterionRepository qaCriterionRepository;

    private Integer diagnosisId;
    private Integer inspectionId;
    private Issue suggested;
    private Issue contract;
    private QaCriterion requirementLine;
    private QaCriterion contractLine;

    @BeforeEach
    void setUp() {
        qaCriterionRepository.deleteAll();
        issueRepository.deleteAll();
        inspectionRepository.deleteAll();
        diagnosisRepository.deleteAll();

        Diagnosis diagnosis = diagnosisRepository.saveAndFlush(Diagnosis.of(8));
        diagnosis.startNextVersion();
        diagnosisId = diagnosisRepository.saveAndFlush(diagnosis).getId();
        Integer firstInspectionId =
                inspectionRepository.saveAndFlush(Inspection.of(diagnosisId, (short) 1, 30, null)).getId();
        inspectionId = inspectionRepository.saveAndFlush(Inspection.of(diagnosisId, (short) 2, 50, null)).getId();

        suggested = Issue.of(inspectionId, "관리자 API에 인증 추가", null, "/admin", 71, "HIGH", false, "fp-admin");
        suggested.putIntoRequirements();
        suggested = issueRepository.saveAndFlush(suggested);
        issueRepository.saveAndFlush(Issue.of(inspectionId, "결제 간헐 실패", null, "/pay", 73, "HIGH", false, "fp-pay"));
        contract = issueRepository.saveAndFlush(Issue.forContract(inspectionId, "관리자 화면에 검색 추가", null, 74));

        // 이번 회차(2)에 사라진 것으로 확인된 지난 회차 결함과, 같은 표시가 붙은 계약용 요구사항
        Issue fixedBefore = Issue.of(firstInspectionId, "옛 결함", null, "/old", 71, "LOW", false, "fp-old");
        fixedBefore.markFixedAt((short) 2);
        issueRepository.saveAndFlush(fixedBefore);
        Issue contractBefore = Issue.forContract(firstInspectionId, "옛 계약 작업", null, 74);
        contractBefore.markFixedAt((short) 2);
        issueRepository.saveAndFlush(contractBefore);

        requirementLine = qaCriterionRepository.saveAndFlush(QaCriterion.of(suggested.getId(), "로그인 화면이 뜬다"));
        contractLine = qaCriterionRepository.saveAndFlush(QaCriterion.forContract(suggested.getId(), "관리자만 들어간다"));
    }

    @Test
    @DisplayName("검사의 요구사항 목록에서 계약용을 뺀다. 요청서 저장·추천 입력·재진단 비교가 쓴다")
    void inspectionListSkipsContractIssues() {
        assertThat(issueRepository.findByInspectionIdAndDeletedFalseOrderByIdAsc(inspectionId))
                .extracting(Issue::getTitle)
                .containsExactly("관리자 API에 인증 추가", "결제 간헐 실패");
    }

    @Test
    @DisplayName("진단서의 요구사항 수에서 계약용을 뺀다")
    void diagnosisCountsSkipContractIssues() {
        assertThat(issueRepository.countByInspectionIdAndDeletedFalse(inspectionId)).isEqualTo(2);
        assertThat(issueRepository.countByInspectionIdAndDeletedFalseAndStatus(inspectionId, "CONTRACTED"))
                .isZero();
        assertThat(issueRepository.countResolvedAt(diagnosisId, (short) 2)).isEqualTo(1);
    }

    @Test
    @DisplayName("요청서 요구사항 수에서 계약용을 뺀다")
    void requestCountSkipsContractIssues() {
        // countInRequest 는 해결로 내린 것만 뺀다. 계약용을 세지 않는지만 본다.
        assertThat(issueRepository.countInRequest(diagnosisId, (short) 2)).isEqualTo(2);
    }

    @Test
    @DisplayName("요구사항 목록 화면과 요청서 화면에서 계약용을 뺀다")
    void pageSkipsContractIssues() {
        assertThat(issueRepository.findPage(inspectionId, null, null, false, PageRequest.of(0, 20)).getContent())
                .extracting(Issue::getTitle)
                .containsExactly("관리자 API에 인증 추가", "결제 간헐 실패");
        assertThat(issueRepository.findPage(inspectionId, null, null, true, PageRequest.of(0, 20)))
                .extracting(Issue::getTitle)
                .containsExactly("관리자 API에 인증 추가");
        assertThat(issueRepository.findPage(inspectionId, null, null, true, PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("요청서의 확인 문장에서 계약서 문장을 뺀다")
    void requirementLinesSkipContractLines() {
        assertThat(qaCriterionRepository.findByIssueIdInAndDeletedFalseOrderByIdAsc(
                        List.of(suggested.getId())))
                .extracting(QaCriterion::getTestCase)
                .containsExactly("로그인 화면이 뜬다");
    }

    @Test
    @DisplayName("계약서 화면이 쓰는 식별자 조회는 계약용도 그대로 돌려준다")
    void contractScreenStillSeesContractRows() {
        assertThat(issueRepository.findAllByIdInAndDeletedFalse(List.of(suggested.getId(), contract.getId())))
                .hasSize(2);
        assertThat(qaCriterionRepository.findAllByIdInAndDeletedFalse(
                        List.of(requirementLine.getId(), contractLine.getId())))
                .hasSize(2);
    }
}
