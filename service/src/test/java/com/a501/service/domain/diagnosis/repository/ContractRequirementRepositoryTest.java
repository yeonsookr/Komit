package com.a501.service.domain.diagnosis.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.entity.Issue;
import com.a501.service.domain.diagnosis.entity.QaCriterion;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 계약서가 쓰는 요구사항 조회와 계약용 표시를 실제 PostgreSQL 18에서 검증한다.
 * (BACKEND_CONVENTIONS 19 Repository 통합 테스트)
 *
 * <p>Spring Data 는 메서드 이름으로 질의를 만들어, 이름이 어긋나도 컴파일은 통과한다. 새 칸이 기본값과
 * 함께 실제로 만들어지는지도 여기서 본다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class ContractRequirementRepositoryTest {
    private static final int DIAGNOSIS_ID = 7;
    private static final int OTHER_DIAGNOSIS_ID = 8;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Autowired private InspectionRepository inspectionRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private QaCriterionRepository qaCriterionRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("검사가 있는 가장 최근 회차를 고른다. 지운 검사와 다른 진단은 빠진다")
    void findsLatestInspectionOfDiagnosis() {
        inspectionRepository.deleteAll();
        inspectionRepository.saveAndFlush(Inspection.of(DIAGNOSIS_ID, (short) 1, 30, null));
        Integer second = inspectionRepository.saveAndFlush(Inspection.of(DIAGNOSIS_ID, (short) 2, 50, null)).getId();
        Inspection deletedThird = Inspection.of(DIAGNOSIS_ID, (short) 3, 70, null);
        ReflectionTestUtils.setField(deletedThird, "deleted", true, boolean.class);
        inspectionRepository.saveAndFlush(deletedThird);
        inspectionRepository.saveAndFlush(Inspection.of(OTHER_DIAGNOSIS_ID, (short) 9, 90, null));

        assertThat(inspectionRepository
                        .findFirstByDiagnosisIdAndDeletedFalseOrderByDiagnosisVersionDesc(DIAGNOSIS_ID)
                        .map(Inspection::getId))
                .contains(second);
    }

    @Test
    @DisplayName("검사가 없는 진단은 비어 있다")
    void emptyWhenNoInspection() {
        inspectionRepository.deleteAll();

        assertThat(inspectionRepository.findFirstByDiagnosisIdAndDeletedFalseOrderByDiagnosisVersionDesc(DIAGNOSIS_ID))
                .isEmpty();
    }

    @Test
    @DisplayName("계약용 표시가 저장되고, 진단에서 나온 요구사항·요청서 문장은 기본값 false 다")
    void storesContractMarker() {
        Integer diagnosed = issueRepository.saveAndFlush(
                Issue.of(55, "관리자 API에 인증 추가", null, "/admin", 71, "HIGH", false, "fp")).getId();
        Integer contracted = issueRepository.saveAndFlush(
                Issue.forContract(55, "관리자 화면에 검색 추가", null, 74)).getId();
        Integer requirementLine = qaCriterionRepository.saveAndFlush(
                QaCriterion.of(diagnosed, "로그인 화면이 뜬다")).getId();
        Integer contractLine = qaCriterionRepository.saveAndFlush(
                QaCriterion.forContract(diagnosed, "관리자만 들어간다")).getId();
        entityManager.clear();

        assertThat(issueRepository.findById(diagnosed)).get().extracting(Issue::isContract).isEqualTo(false);
        assertThat(issueRepository.findById(contracted)).get().extracting(Issue::isContract).isEqualTo(true);
        assertThat(qaCriterionRepository.findById(requirementLine)).get()
                .extracting(QaCriterion::isContract).isEqualTo(false);
        assertThat(qaCriterionRepository.findById(contractLine)).get()
                .extracting(QaCriterion::isContract).isEqualTo(true);
    }

    @Test
    @DisplayName("칸을 비운 채 넣은 옛 행도 기본값 false 로 채워진다")
    void columnDefaultFillsOldRows() {
        entityManager.createNativeQuery("""
                INSERT INTO qa_criteria (issue_id, test_case, status, status_group,
                    record_status, record_status_group, is_deleted, created_at, updated_at)
                VALUES (1, '옛 문장', 'UNRESOLVED', 'QA_CRITERION_STATUS', 'ACTIVE', 'RECORD_STATUS', FALSE, now(), now())
                """).executeUpdate();

        entityManager.createNativeQuery("""
                INSERT INTO issues (inspection_id, title, category_code_id, status, status_group,
                    severity, severity_group, record_status, record_status_group, is_deleted, created_at)
                VALUES (55, '옛 요구사항', 71, 'UNRESOLVED', 'ISSUE_STATUS', 'HIGH', 'ISSUE_SEVERITY',
                    'ACTIVE', 'RECORD_STATUS', FALSE, now())
                """).executeUpdate();

        assertThat(entityManager
                        .createNativeQuery("SELECT is_contract FROM qa_criteria WHERE test_case = '옛 문장'")
                        .getSingleResult())
                .isEqualTo(false);
        assertThat(entityManager
                        .createNativeQuery("SELECT is_contract FROM issues WHERE title = '옛 요구사항'")
                        .getSingleResult())
                .isEqualTo(false);
    }
}
