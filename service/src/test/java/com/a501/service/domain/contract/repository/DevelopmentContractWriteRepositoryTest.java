package com.a501.service.domain.contract.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.contract.entity.Deliverable;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.entity.MilestoneQaResult;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 개발 계약서를 저장할 때 만드는 행이 실제 PostgreSQL 18의 칸 제약을 지키는지 본다. 비울 수 없는 상태 칸과
 * 새로 생긴 작업의 이름·설명 칸이 대상이다. (BACKEND_CONVENTIONS 19 Repository 통합 테스트)
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class DevelopmentContractWriteRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Autowired private DevelopmentContractRepository developmentContractRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private MilestoneQaResultRepository milestoneQaResultRepository;
    @Autowired private DeliverableRepository deliverableRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("개발 계약·작업·확인 문장 연결·인계물이 저장되고 다시 읽힌다")
    void storesWrittenContractRows() {
        Integer developmentId = developmentContractRepository.saveAndFlush(
                DevelopmentContract.of(130, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14)).getId();
        Integer renamed = milestoneRepository.saveAndFlush(
                Milestone.of(developmentId, 312, "관리자 인증 강화", "토큰도 확인한다", 400000, 10)).getId();
        Integer plain = milestoneRepository.saveAndFlush(
                Milestone.of(developmentId, 320, null, null, 0, null)).getId();
        milestoneQaResultRepository.saveAndFlush(MilestoneQaResult.of(renamed, 5001));
        deliverableRepository.saveAndFlush(Deliverable.of(developmentId, "소스 파일 전체"));
        entityManager.clear();

        assertThat(developmentContractRepository.findByContractIdAndDeletedFalse(130)).get()
                .satisfies(development -> {
                    assertThat(development.getStatus()).isEqualTo("SIGNED");
                    assertThat(development.getStatusGroup()).isEqualTo("DEVELOPMENT_CONTRACT_STATUS");
                });
        assertThat(milestoneRepository.findById(renamed)).get().satisfies(milestone -> {
            assertThat(milestone.getTitle()).isEqualTo("관리자 인증 강화");
            assertThat(milestone.getDetail()).isEqualTo("토큰도 확인한다");
            assertThat(milestone.getStatus()).isEqualTo("PROPOSED");
        });
        assertThat(milestoneRepository.findById(plain)).get().satisfies(milestone -> {
            assertThat(milestone.getTitle()).isNull();
            assertThat(milestone.getDetail()).isNull();
        });
        assertThat(milestoneQaResultRepository.findAllByMilestoneIdInAndDeletedFalseOrderByIdAsc(
                        java.util.List.of(renamed)))
                .extracting(MilestoneQaResult::getQaCriterionId).containsExactly(5001);
        assertThat(deliverableRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(developmentId))
                .extracting(Deliverable::getTitle).containsExactly("소스 파일 전체");
    }

    @Test
    @DisplayName("다시 써서 빠진 작업과 인계물은 조회에서 빠진다")
    void removedRowsDropOutOfQueries() {
        Integer developmentId = developmentContractRepository.saveAndFlush(
                DevelopmentContract.of(131, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14)).getId();
        Milestone old = milestoneRepository.saveAndFlush(Milestone.of(developmentId, 312, null, null, 1, null));
        Deliverable oldDeliverable = deliverableRepository.saveAndFlush(Deliverable.of(developmentId, "옛 인계물"));
        old.removeFromContract(LocalDateTime.now());
        oldDeliverable.removeFromContract(LocalDateTime.now());
        milestoneRepository.saveAndFlush(old);
        deliverableRepository.saveAndFlush(oldDeliverable);
        milestoneRepository.saveAndFlush(Milestone.of(developmentId, 311, null, null, 1, null));

        assertThat(milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(developmentId))
                .extracting(Milestone::getIssueId).containsExactly(311);
        assertThat(deliverableRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(developmentId))
                .isEmpty();
    }
}
