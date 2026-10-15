package com.a501.service.domain.developer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.developer.entity.PortfolioItem;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 작업물 목록이 등록순이고 소프트 삭제된 행이 빠지는지 실제 PostgreSQL 18에서 봅니다. (BACKEND_CONVENTIONS 19) */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class PortfolioItemRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Autowired
    private PortfolioItemRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void listsOnlyLivingItemsOfTheDeveloperInRegistrationOrder() {
        PortfolioItem first = repository.save(PortfolioItem.create(3, "첫째", null, null, null));
        PortfolioItem removed = repository.save(PortfolioItem.create(3, "지움", null, null, 512));
        PortfolioItem second = repository.save(PortfolioItem.create(3, "둘째", "설명", "https://a.example", 513));
        repository.save(PortfolioItem.create(4, "남의 것", null, null, null));
        removed.remove(LocalDateTime.of(2026, 9, 21, 3, 0));
        repository.flush();

        List<PortfolioItem> found = repository.findByDeveloperIdAndDeletedFalseOrderByIdAsc(3);

        assertThat(found).extracting(PortfolioItem::getId).containsExactly(first.getId(), second.getId());
        assertThat(repository.findById(removed.getId())).get()
                .satisfies(item -> {
                    assertThat(item.isDeleted()).isTrue();
                    assertThat(item.getRecordStatus()).isEqualTo("DELETED");
                    assertThat(item.getDeletedAt()).isNotNull();
                });
    }
}
