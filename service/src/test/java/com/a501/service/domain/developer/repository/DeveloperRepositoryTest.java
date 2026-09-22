package com.a501.service.domain.developer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.developer.dto.DeveloperSearchCondition;
import com.a501.service.domain.developer.dto.DeveloperSearchRow;
import com.a501.service.domain.developer.dto.LowestPriceView;
import com.a501.service.domain.developer.dto.request.DeveloperSort;
import com.a501.service.domain.developer.entity.Career;
import com.a501.service.domain.developer.entity.Developer;
import com.a501.service.domain.developer.entity.DeveloperProfile;
import com.a501.service.domain.developer.entity.PriceCatalog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 공개 개발자 목록의 QueryDSL 조회를 실제 PostgreSQL 18에서 검증합니다. (BACKEND_CONVENTIONS 19 Repository 통합 테스트)
 * 프로필 없는 개발자·삭제된 행 제외, 분야·사용자 조건, 평점·가격 정렬의 null 뒤 보내기, 페이지 경계, 추천 순서가 대상입니다.
 *
 * <p>시드 데이터: 개발자 A(평점 4.4/4.9, 가격 5만·10만, 분야 42, 증빙 확인), B(평점 4.7/4.1, 가격 6만, 분야 47),
 * C(평점 없음, 가격 없음, 분야 없음), D(프로필 없음 → 목록에서 빠짐), E(삭제됨 → 빠짐).
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class DeveloperRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Autowired
    private DeveloperRepository developerRepository;
    @Autowired
    private DeveloperProfileRepository profileRepository;
    @Autowired
    private PriceCatalogRepository priceCatalogRepository;
    @Autowired
    private CareerRepository careerRepository;

    private Integer a;
    private Integer b;
    private Integer c;

    @BeforeEach
    void setUp() {
        priceCatalogRepository.deleteAll();
        careerRepository.deleteAll();
        profileRepository.deleteAll();
        developerRepository.deleteAll();

        a = developer(1001, 4.4, 2, 4.9, 2);
        b = developer(1002, 4.7, 5, 4.1, 5);
        c = developer(1003, null, 0, null, 0);
        Integer d = developer(1004, 5.0, 9, 5.0, 9);
        Integer e = developer(1005, 5.0, 9, 5.0, 9);

        profileRepository.save(DeveloperProfile.create(a, "A 소개", "설명", new BigDecimal("5.0"), 42, null, null));
        profileRepository.save(DeveloperProfile.create(b, "B 소개", null, new BigDecimal("6.0"), 47, null, null));
        profileRepository.save(DeveloperProfile.create(c, "C 소개", null, null, null, null, null));
        profileRepository.save(DeveloperProfile.create(e, "E 소개", null, null, 42, null, null));
        Developer deleted = developerRepository.findById(e).orElseThrow();
        ReflectionTestUtils.invokeMethod(deleted, "markDeleted", LocalDateTime.of(2026, 9, 21, 3, 0));

        priceCatalogRepository.save(PriceCatalog.create(a, "CONSULT", "30분", 50_000L, null));
        priceCatalogRepository.save(PriceCatalog.create(a, "DEVELOP", "로그인", 100_000L, (short) 4));
        priceCatalogRepository.save(PriceCatalog.create(a, "DEVELOP", "배포", 200_000L, (short) 1));
        PriceCatalog removed = priceCatalogRepository.save(PriceCatalog.create(a, "CONSULT", "지운 값", 1_000L, null));
        removed.remove(LocalDateTime.of(2026, 9, 21, 3, 0));
        priceCatalogRepository.save(PriceCatalog.create(b, "DEVELOP", "버그", 60_000L, (short) 2));

        Career verified = careerRepository.save(Career.create(a, "회사", "역할", LocalDate.of(2021, 3, 1), null, null));
        ReflectionTestUtils.setField(verified, "verified", true);
        careerRepository.save(Career.create(b, "회사", "역할", LocalDate.of(2021, 3, 1), null, null));
        careerRepository.flush();
        developerRepository.flush();
    }

    private Integer developer(int userId, Double devRating, int devCount, Double expRating, int expCount) {
        Developer developer = Developer.register(userId);
        ReflectionTestUtils.setField(developer, "devRating", devRating);
        ReflectionTestUtils.setField(developer, "devReviewCount", devCount);
        ReflectionTestUtils.setField(developer, "explanatoryRating", expRating);
        ReflectionTestUtils.setField(developer, "explanatoryReviewCount", expCount);
        return developerRepository.save(developer).getId();
    }

    @Test
    @DisplayName("프로필이 없거나 삭제된 개발자는 목록·전체 수·행 조회 어디에도 나오지 않는다")
    void onlyLivingDevelopersWithProfiles() {
        assertThat(developerRepository.countActive()).isEqualTo(3);
        Page<DeveloperSearchRow> page = developerRepository.searchPage(
                DeveloperSearchCondition.none(), PageRequest.of(0, 10, DeveloperSort.DEV_RATING_DESC.toSort()));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(DeveloperSearchRow::developerId).containsExactly(b, a, c);
        assertThat(developerRepository.searchIds(DeveloperSearchCondition.none())).containsExactly(a, b, c);
        assertThat(developerRepository.findRows(List.of(a, c, Integer.MAX_VALUE)))
                .extracting(DeveloperSearchRow::developerId).containsExactlyInAnyOrder(a, c);
    }

    @Test
    @DisplayName("행에는 개발자·프로필 컬럼이 그대로 담긴다")
    void rowCarriesProfileColumns() {
        DeveloperSearchRow row = developerRepository.findRows(List.of(a)).get(0);

        assertThat(row.userId()).isEqualTo(1001);
        assertThat(row.intro()).isEqualTo("A 소개");
        assertThat(row.description()).isEqualTo("설명");
        assertThat(row.fieldCodeId()).isEqualTo(42);
        assertThat(row.yearsOfExperience()).isEqualByComparingTo("5.0");
        assertThat(row.devRating()).isEqualTo(4.4);
        assertThat(row.devReviewCount()).isEqualTo(2);
        assertThat(row.explanatoryRating()).isEqualTo(4.9);
    }

    @Test
    @DisplayName("분야 조건은 하나라도 맞는 개발자를 찾고, 아는 분야가 아닌 값은 아무도 맞지 않는다")
    void fieldCondition() {
        assertThat(developerRepository.searchIds(new DeveloperSearchCondition(List.of(42, 47), null))).containsExactly(a, b);
        assertThat(developerRepository.searchIds(new DeveloperSearchCondition(List.of(47), null))).containsExactly(b);
        assertThat(developerRepository.searchIds(new DeveloperSearchCondition(List.of(999), null))).isEmpty();
    }

    @Test
    @DisplayName("사용자 조건은 이름 검색 결과를 개발자로 좁힌다")
    void userCondition() {
        assertThat(developerRepository.searchIds(new DeveloperSearchCondition(null, List.of(1001, 1003)))).containsExactly(a, c);
        assertThat(developerRepository.searchIds(new DeveloperSearchCondition(List.of(42), List.of(1003)))).isEmpty();
    }

    @Test
    @DisplayName("평점 정렬은 내림차순이고 평점이 없는 개발자는 뒤로 간다")
    void ratingSortsPutNullsLast() {
        assertThat(developerRepository.searchPage(DeveloperSearchCondition.none(),
                        PageRequest.of(0, 10, DeveloperSort.DEV_RATING_DESC.toSort())).getContent())
                .extracting(DeveloperSearchRow::developerId).containsExactly(b, a, c);
        assertThat(developerRepository.searchPage(DeveloperSearchCondition.none(),
                        PageRequest.of(0, 10, DeveloperSort.EXPLANATORY_RATING_DESC.toSort())).getContent())
                .extracting(DeveloperSearchRow::developerId).containsExactly(a, b, c);
    }

    @Test
    @DisplayName("비용 정렬은 살아 있는 가격표 전체의 최저가 오름차순이고 가격표가 없는 개발자는 뒤로 간다")
    void priceSortUsesLowestLivingPrice() {
        // A의 최저가는 지운 1,000원이 아니라 50,000원이고, B는 60,000원이다.
        assertThat(developerRepository.searchPage(DeveloperSearchCondition.none(),
                        PageRequest.of(0, 10, DeveloperSort.PRICE_ASC.toSort())).getContent())
                .extracting(DeveloperSearchRow::developerId).containsExactly(a, b, c);
    }

    @Test
    @DisplayName("페이지 크기와 경계를 지키고 마지막 페이지를 넘어서면 빈 목록에 전체 건수는 채워진다")
    void pagingBoundaries() {
        Sort sort = DeveloperSort.DEV_RATING_DESC.toSort();
        Page<DeveloperSearchRow> first = developerRepository.searchPage(DeveloperSearchCondition.none(), PageRequest.of(0, 2, sort));
        Page<DeveloperSearchRow> second = developerRepository.searchPage(DeveloperSearchCondition.none(), PageRequest.of(1, 2, sort));
        Page<DeveloperSearchRow> beyond = developerRepository.searchPage(DeveloperSearchCondition.none(), PageRequest.of(5, 2, sort));

        assertThat(first.getContent()).extracting(DeveloperSearchRow::developerId).containsExactly(b, a);
        assertThat(second.getContent()).extracting(DeveloperSearchRow::developerId).containsExactly(c);
        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(3);
        assertThat(beyond.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("지원하지 않는 정렬 속성은 코드 오류로 보고 예외를 던진다")
    void unsupportedSortPropertyFails() {
        assertThatThrownBy(() -> developerRepository.searchPage(
                DeveloperSearchCondition.none(), PageRequest.of(0, 2, Sort.by("intro"))))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    @DisplayName("추천 개발자는 개발력 평점 높은 순이고 평점 없는 개발자는 뒤로, 요청한 수만큼만 돌려준다")
    void featuredDevelopers() {
        assertThat(developerRepository.findFeatured(2)).extracting(DeveloperSearchRow::developerId).containsExactly(b, a);
        assertThat(developerRepository.findFeatured(10)).extracting(DeveloperSearchRow::developerId).containsExactly(b, a, c);
    }

    @Test
    @DisplayName("최저가 집계는 살아 있는 줄만 보고 개발자·작업 종류별로 한 줄이며 컨설팅이 개발보다 먼저다")
    void lowestPricesAggregate() {
        List<LowestPriceView> views = priceCatalogRepository.findLowestPrices(List.of(a, b, c));

        assertThat(views).extracting(LowestPriceView::getDeveloperId, LowestPriceView::getTaskType, LowestPriceView::getLowestPrice)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(a, "CONSULT", 50_000L),
                        org.assertj.core.groups.Tuple.tuple(a, "DEVELOP", 100_000L),
                        org.assertj.core.groups.Tuple.tuple(b, "DEVELOP", 60_000L));
    }

    @Test
    @DisplayName("증빙 확인 개발자 집계는 확인된 살아 있는 경력이 있는 개발자만 돌려준다")
    void verifiedDeveloperIds() {
        assertThat(careerRepository.findVerifiedDeveloperIds(List.of(a, b, c))).containsExactly(a);
    }
}
