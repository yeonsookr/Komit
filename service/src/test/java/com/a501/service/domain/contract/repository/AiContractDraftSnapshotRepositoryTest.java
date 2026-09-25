package com.a501.service.domain.contract.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.contract.entity.AiContractDraftSnapshot;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class AiContractDraftSnapshotRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Autowired private AiContractDraftSnapshotRepository repository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("생성 당시 JSON을 PostgreSQL JSON 컬럼에 그대로 저장하고 계약으로 찾는다")
    void savesAndFindsOriginalJson() {
        String original = "{\"draft\":{\"topic\":\"인증 오류\",\"detail\":null}}";
        repository.saveAndFlush(AiContractDraftSnapshot.of(31, original));

        assertThat(repository.findByContractId(31))
                .hasValueSatisfying(found -> assertThat(found.getContentSnapshot()).isEqualTo(original));
    }

    @Test
    @DisplayName("한 계약에 AI 원본을 두 번 저장할 수 없다")
    void rejectsSecondSnapshotForSameContract() {
        repository.saveAndFlush(AiContractDraftSnapshot.of(31, "{\"draft\":{\"topic\":\"첫 판\"}}"));

        assertThatThrownBy(() -> repository.saveAndFlush(
                        AiContractDraftSnapshot.of(31, "{\"draft\":{\"topic\":\"다른 판\"}}")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("저장된 AI 원본을 엔티티 값 변경으로 덮어쓸 수 없다")
    void neverUpdatesStoredContent() {
        AiContractDraftSnapshot saved = repository.saveAndFlush(
                AiContractDraftSnapshot.of(31, "{\"draft\":{\"topic\":\"최초 원본\"}}"));
        ReflectionTestUtils.setField(
                saved, "contentSnapshot", "{\"draft\":{\"topic\":\"덮어쓴 값\"}}");
        repository.saveAndFlush(saved);
        entityManager.clear();

        assertThat(repository.findByContractId(31))
                .hasValueSatisfying(found ->
                        assertThat(found.getContentSnapshot()).contains("최초 원본"));
    }
}
