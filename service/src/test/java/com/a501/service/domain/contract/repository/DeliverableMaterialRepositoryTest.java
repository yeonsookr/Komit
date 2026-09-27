package com.a501.service.domain.contract.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.contract.entity.DeliverableAccessStage;
import com.a501.service.domain.contract.entity.DeliverableMaterial;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 인계 자료와 인계 작업 표시가 실제 PostgreSQL 18의 칸 제약을 지키는지 본다. (R-QA-06, BACKEND_CONVENTIONS 19
 * Repository 통합 테스트)
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class DeliverableMaterialRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    private static final String CHECK = "ck_deliverable_materials_file_or_url";

    @Autowired private DeliverableMaterialRepository materialRepository;
    @Autowired private DevelopmentContractRepository developmentContractRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("한 산출물에 URL 여러 개와 TEST·PAID 파일이 함께 저장되고 다시 읽힌다")
    void storesUrlsAndFilesTogether() {
        materialRepository.saveAndFlush(DeliverableMaterial.ofFile(401, 3_000_000_001L, DeliverableAccessStage.TEST));
        materialRepository.saveAndFlush(DeliverableMaterial.ofFile(401, 3_000_000_002L, DeliverableAccessStage.PAID));
        materialRepository.saveAndFlush(DeliverableMaterial.ofUrl(401, "https://demo.example.com"));
        materialRepository.saveAndFlush(DeliverableMaterial.ofUrl(401, "https://staging.example.com"));
        entityManager.clear();

        List<DeliverableMaterial> materials =
                materialRepository.findAllByDeliverableIdInAndDeletedFalseOrderByIdAsc(List.of(401));

        assertThat(materials).extracting(DeliverableMaterial::getFileId)
                .containsExactly(3_000_000_001L, 3_000_000_002L, null, null);
        assertThat(materials).extracting(DeliverableMaterial::getAccessStage).containsExactly(
                DeliverableAccessStage.TEST, DeliverableAccessStage.PAID,
                DeliverableAccessStage.TEST, DeliverableAccessStage.TEST);
    }

    @Test
    @DisplayName("한 행에 파일과 URL이 함께 있으면 DB가 거부한다")
    void rejectsRowWithBothFileAndUrl() {
        assertThatThrownBy(() -> insert(401, 71L, "https://demo.example.com", "TEST"))
                .hasMessageContaining(CHECK);
    }

    @Test
    @DisplayName("한 행에 파일도 URL도 없으면 DB가 거부한다")
    void rejectsRowWithNeither() {
        assertThatThrownBy(() -> insert(401, null, null, "TEST")).hasMessageContaining(CHECK);
    }

    @Test
    @DisplayName("URL 자료를 PAID로 두면 DB가 거부한다")
    void rejectsPaidUrl() {
        assertThatThrownBy(() -> insert(401, null, "https://demo.example.com", "PAID"))
                .hasMessageContaining(CHECK);
    }

    @Test
    @DisplayName("같은 산출물에 같은 파일을 두 번 넣으면 DB가 거부한다")
    void rejectsDuplicateFileInOneDeliverable() {
        materialRepository.saveAndFlush(DeliverableMaterial.ofFile(401, 71L, DeliverableAccessStage.TEST));

        assertThatThrownBy(() -> materialRepository.saveAndFlush(
                DeliverableMaterial.ofFile(401, 71L, DeliverableAccessStage.PAID)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_deliverable_materials_deliverable_file");
    }

    @Test
    @DisplayName("같은 산출물에 같은 URL을 두 번 넣으면 DB가 거부한다")
    void rejectsDuplicateUrlInOneDeliverable() {
        materialRepository.saveAndFlush(DeliverableMaterial.ofUrl(401, "https://demo.example.com"));

        assertThatThrownBy(() -> materialRepository.saveAndFlush(
                DeliverableMaterial.ofUrl(401, "https://demo.example.com")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_deliverable_materials_deliverable_url");
    }

    @Test
    @DisplayName("인계 작업 표시는 저장되고, 표시 없이 넣은 기존 행은 일반 작업으로 채워진다")
    void storesHandoverMarkWithFalseDefault() {
        Integer developmentId = developmentContractRepository.saveAndFlush(
                DevelopmentContract.of(130, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14)).getId();
        Integer handover = milestoneRepository.saveAndFlush(
                Milestone.of(developmentId, 320, null, null, 0, null, true)).getId();
        entityManager.createNativeQuery("""
                insert into milestones (development_contract_id, issue_id, price, status, status_group,
                                        record_status, record_status_group)
                values (:developmentId, 312, 0, 'PROPOSED', 'MILESTONE_STATUS', 'ACTIVE', 'RECORD_STATUS')
                """).setParameter("developmentId", developmentId).executeUpdate();
        entityManager.clear();

        assertThat(milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(developmentId))
                .extracting(milestone -> milestone.getId().equals(handover), Milestone::isHandover)
                .containsExactlyInAnyOrder(
                        tuple(true, true),
                        tuple(false, false));
    }

    private void insert(Integer deliverableId, Long fileId, String url, String accessStage) {
        entityManager.createNativeQuery("""
                insert into deliverable_materials (deliverable_id, file_id, url, access_stage,
                                                   record_status, record_status_group)
                values (:deliverableId, :fileId, :url, :accessStage, 'ACTIVE', 'RECORD_STATUS')
                """)
                .setParameter("deliverableId", deliverableId)
                .setParameter("fileId", fileId)
                .setParameter("url", url)
                .setParameter("accessStage", accessStage)
                .executeUpdate();
    }
}
