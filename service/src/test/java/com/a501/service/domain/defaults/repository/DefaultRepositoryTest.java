package com.a501.service.domain.defaults.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.defaults.dto.response.DefaultResponse;
import com.a501.service.domain.defaults.entity.Default;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * QueryDSL 목록 조회를 실제 PostgreSQL 18에서 검증합니다. (BACKEND_CONVENTIONS 19 Repository 통합 테스트)
 * 소프트 삭제 제외, 정렬, 페이지 경계, count 동작이 대상입니다. H2로 대체하지 않습니다.
 *
 * <p>{@link JpaConfig}를 올려 {@code JPAQueryFactory}와 감사 시각 주입을 함께 씁니다. 시드({@code data.sql})가 넣는 행이
 * 결과에 섞이지 않도록 테스트마다 테이블을 비우고 시작합니다. 각 테스트는 트랜잭션 안에서 돌고 끝나면 롤백됩니다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class DefaultRepositoryTest {
    private static final Sort ID_ASC = Sort.by(Sort.Direction.ASC, "id");
    private static final Sort ID_DESC = Sort.by(Sort.Direction.DESC, "id");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Autowired
    private DefaultRepository defaultRepository;

    private List<Integer> activeIds;
    private Integer deletedId;

    @BeforeEach
    void setUp() {
        defaultRepository.deleteAll();
        activeIds = IntStream.rangeClosed(1, 4)
                .mapToObj(index -> defaultRepository.save(Default.of("default")).getId())
                .toList();
        Default deleted = Default.of("deleted");
        deleted.delete(LocalDateTime.now(ZoneOffset.UTC));
        deletedId = defaultRepository.saveAndFlush(deleted).getId();
    }

    @Test
    @DisplayName("단건 조회는 살아 있는 행만 찾고 삭제된 행과 없는 id는 비어 있다")
    void findByIdAndDeletedFalseExcludesDeleted() {
        assertThat(defaultRepository.findByIdAndDeletedFalse(activeIds.get(0)))
                .hasValueSatisfying(found -> assertThat(found.getMessage()).isEqualTo("default"));
        assertThat(defaultRepository.findByIdAndDeletedFalse(deletedId)).isEmpty();
        assertThat(defaultRepository.findByIdAndDeletedFalse(Integer.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("첫 페이지는 id 오름차순으로 크기만큼 담고 전체 건수에서 삭제된 행을 뺀다")
    void firstPageAscending() {
        Page<DefaultResponse> page = defaultRepository.findActivePage(PageRequest.of(0, 3, ID_ASC));

        assertThat(page.getContent()).extracting(DefaultResponse::id)
                .containsExactly(activeIds.get(0), activeIds.get(1), activeIds.get(2));
        assertThat(page.getContent()).extracting(DefaultResponse::message).containsOnly("default");
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("마지막 페이지는 남은 항목만 담고 내림차순 정렬을 그대로 따른다")
    void lastPageDescending() {
        Page<DefaultResponse> page = defaultRepository.findActivePage(PageRequest.of(1, 3, ID_DESC));

        assertThat(page.getContent()).extracting(DefaultResponse::id).containsExactly(activeIds.get(0));
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("마지막 페이지를 넘어선 요청은 빈 목록이지만 전체 건수는 채워진다")
    void beyondLastPageIsEmptyWithTotals() {
        Page<DefaultResponse> page = defaultRepository.findActivePage(PageRequest.of(5, 3, ID_ASC));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("살아 있는 행이 없으면 전체 건수와 페이지 수가 0이다")
    void emptyTableHasZeroPages() {
        defaultRepository.deleteAll();

        Page<DefaultResponse> page = defaultRepository.findActivePage(PageRequest.of(0, 20, ID_ASC));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getTotalPages()).isZero();
    }

    @Test
    @DisplayName("정렬을 주지 않으면 id 오름차순으로 고정해 페이지 경계가 흔들리지 않게 한다")
    void unsortedFallsBackToIdAscending() {
        Page<DefaultResponse> page = defaultRepository.findActivePage(PageRequest.of(0, 4));

        assertThat(page.getContent()).extracting(DefaultResponse::id).containsExactlyElementsOf(activeIds);
    }

    @Test
    @DisplayName("id 외의 정렬 속성은 코드 오류로 보고 거절한다")
    void rejectsUnsupportedSortProperty() {
        PageRequest byMessage = PageRequest.of(0, 3, Sort.by("message"));

        // 구현은 IllegalArgumentException을 던지지만, Spring Data 리포지토리 프록시의 예외 변환이 DataAccessException 계열로 감쌉니다.
        assertThatThrownBy(() -> defaultRepository.findActivePage(byMessage))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported sort property: message");
    }
}
