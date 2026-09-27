package com.a501.service.domain.contract.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
 * 작업 확인이 계약 행을 잠그려고 쓰는 계약 id 조회와 자동 통과 대상 조회를 실제 PostgreSQL 18에서 본다. (R-QA-02,
 * BACKEND_CONVENTIONS 19 Repository 통합 테스트)
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class MilestoneRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 9, 24, 10, 0);

    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private DevelopmentContractRepository developmentContractRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("작업이 속한 계약 id를 작업 엔티티를 읽지 않고 가져온다. 지운 작업은 없다")
    void findsContractIdOfTheMilestone() {
        Integer developmentId = development(130);
        Milestone kept = milestoneRepository.saveAndFlush(milestone(developmentId, "IN_PROGRESS", null));
        Milestone removed = milestone(developmentId, "PROPOSED", null);
        removed.removeFromContract(CUTOFF);
        removed = milestoneRepository.saveAndFlush(removed);
        entityManager.clear();

        assertThat(milestoneRepository.findContractIdByMilestoneId(kept.getId())).contains(130);
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(
                entityManager.getReference(Milestone.class, kept.getId()))).isFalse();
        assertThat(milestoneRepository.findContractIdByMilestoneId(removed.getId())).isEmpty();
        assertThat(milestoneRepository.findContractIdByMilestoneId(999_999)).isEmpty();
    }

    @Test
    @DisplayName("기준 시각까지 요청하고 답이 없는 작업만 자동 통과 대상이다. 기준 시각과 같으면 대상이다")
    void findsSubmissionsDueForAutoPass() {
        Integer developmentId = development(131);
        Integer atCutoff = milestoneRepository.saveAndFlush(milestone(developmentId, "SUBMITTED", CUTOFF)).getId();
        Integer before = milestoneRepository.saveAndFlush(
                milestone(developmentId, "SUBMITTED", CUTOFF.minusDays(1))).getId();
        milestoneRepository.saveAndFlush(milestone(developmentId, "SUBMITTED", CUTOFF.plusSeconds(1)));
        milestoneRepository.saveAndFlush(milestone(developmentId, "IN_PROGRESS", CUTOFF.minusDays(1)));
        milestoneRepository.saveAndFlush(milestone(developmentId, "CONFIRMED", CUTOFF.minusDays(1)));
        Milestone removed = milestone(developmentId, "SUBMITTED", CUTOFF.minusDays(1));
        removed.removeFromContract(CUTOFF);
        milestoneRepository.saveAndFlush(removed);
        entityManager.clear();

        assertThat(milestoneRepository.findSubmittedIdsBefore(CUTOFF)).containsExactly(atCutoff, before);
    }

    @Test
    @DisplayName("지급할 계약을 고를 때 진행 중인 개발 계약과 그 작업을 지운 것 없이 한 번에 읽는다")
    void findsInProgressDevelopmentsAndTheirWork() {
        Integer started = development(132);
        developmentContractRepository.findById(started).orElseThrow().start();
        Integer notStarted = development(133);
        developmentContractRepository.flush();
        Integer kept = milestoneRepository.saveAndFlush(milestone(started, "CONFIRMED", null)).getId();
        Milestone removed = milestone(started, "CONFIRMED", null);
        removed.removeFromContract(CUTOFF);
        milestoneRepository.saveAndFlush(removed);
        Integer other = milestoneRepository.saveAndFlush(milestone(notStarted, "PROPOSED", null)).getId();
        entityManager.clear();

        assertThat(developmentContractRepository.findByStatusAndDeletedFalse("IN_PROGRESS"))
                .extracting(DevelopmentContract::getId).containsExactly(started);
        assertThat(milestoneRepository.findAllByDevelopmentContractIdInAndDeletedFalse(List.of(started, notStarted)))
                .extracting(Milestone::getId).containsExactlyInAnyOrder(kept, other);
    }

    private Integer development(int contractId) {
        return developmentContractRepository.saveAndFlush(
                DevelopmentContract.of(contractId, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14)).getId();
    }

    private static Milestone milestone(Integer developmentId, String status, LocalDateTime submittedAt) {
        Milestone milestone = Milestone.of(developmentId, 312, null, null, 100000, null, false);
        ReflectionTestUtils.setField(milestone, "status", status);
        ReflectionTestUtils.setField(milestone, "submittedAt", submittedAt);
        return milestone;
    }
}
