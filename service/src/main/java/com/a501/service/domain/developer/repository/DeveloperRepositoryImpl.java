package com.a501.service.domain.developer.repository;

import com.a501.service.domain.developer.dto.DeveloperSearchCondition;
import com.a501.service.domain.developer.dto.DeveloperSearchRow;
import com.a501.service.domain.developer.dto.request.DeveloperSort;
import com.a501.service.domain.developer.entity.QDeveloper;
import com.a501.service.domain.developer.entity.QDeveloperProfile;
import com.a501.service.domain.developer.entity.QPriceCatalog;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

/**
 * {@link DeveloperRepositoryCustom}의 QueryDSL 구현. (BACKEND_CONVENTIONS 13.3 성능 규칙)
 *
 * <ul>
 *   <li>개발자 도메인의 테이블(developers, developer_profiles, price_catalogs)만 조인합니다. 이름·사진·코드 이름·계약 수는 다른
 *       도메인 서비스가 식별자로 채우므로 여기서 조인하지 않습니다. (7.1)</li>
 *   <li>필요한 컬럼만 {@code Projections.constructor}로 {@link DeveloperSearchRow}에 직접 담습니다.</li>
 *   <li>{@code limit}·{@code offset}을 항상 적용하고, count 쿼리는 정렬을 뺀 별도 쿼리로 두어 필요할 때만 실행합니다.</li>
 *   <li>조건은 {@link BooleanExpression}을 돌려주는 메서드로 표현하고, 조건이 없으면 {@code null}을 돌려 {@code where}에서 빠지게 합니다.</li>
 *   <li>비용 정렬의 기준인 가격표 최저가는 상관 서브쿼리로 정렬 식에만 넣습니다. 컬렉션 조인으로 행을 늘리지 않기 위해서입니다.</li>
 * </ul>
 */
@RequiredArgsConstructor
public class DeveloperRepositoryImpl implements DeveloperRepositoryCustom {
    private static final QDeveloper D = QDeveloper.developer;
    private static final QDeveloperProfile P = QDeveloperProfile.developerProfile;
    private static final QPriceCatalog PC = QPriceCatalog.priceCatalog;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<DeveloperSearchRow> searchPage(DeveloperSearchCondition condition, Pageable pageable) {
        List<DeveloperSearchRow> content = baseQuery(row())
                .where(fieldIn(condition.fieldCodeIds()), userIn(condition.userIds()))
                .orderBy(orderSpecifiers(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = baseQuery(D.count())
                .where(fieldIn(condition.fieldCodeIds()), userIn(condition.userIds()));

        return PageableExecutionUtils.getPage(content, pageable,
                () -> Optional.ofNullable(countQuery.fetchOne()).orElse(0L));
    }

    @Override
    public List<Integer> searchIds(DeveloperSearchCondition condition) {
        return baseQuery(D.id)
                .where(fieldIn(condition.fieldCodeIds()), userIn(condition.userIds()))
                .orderBy(D.id.asc())
                .fetch();
    }

    @Override
    public List<DeveloperSearchRow> findRows(Collection<Integer> developerIds) {
        if (developerIds == null || developerIds.isEmpty()) {
            return List.of();
        }
        return baseQuery(row())
                .where(D.id.in(developerIds))
                .fetch();
    }

    @Override
    public long countActive() {
        return Optional.ofNullable(baseQuery(D.count()).fetchOne()).orElse(0L);
    }

    @Override
    public List<DeveloperSearchRow> findFeatured(int size) {
        return baseQuery(row())
                .orderBy(D.devRating.desc().nullsLast(), D.devReviewCount.desc(), D.id.asc())
                .limit(size)
                .fetch();
    }

    /** 살아 있는 개발자에 살아 있는 프로필을 붙인 기본 조회. 모든 공개 조회가 이 위에서 시작합니다. */
    private <T> JPAQuery<T> baseQuery(com.querydsl.core.types.Expression<T> select) {
        return queryFactory
                .select(select)
                .from(D)
                .join(P).on(P.developerId.eq(D.id).and(P.deleted.isFalse()))
                .where(D.deleted.isFalse());
    }

    /** 필드 순서는 {@link DeveloperSearchRow} 생성자와 같아야 합니다. */
    private static ConstructorExpression<DeveloperSearchRow> row() {
        return Projections.constructor(DeveloperSearchRow.class,
                D.id, D.userId, P.intro, P.fieldCodeId, P.yearsOfExperience, P.description, P.activeFrom, P.activeTo,
                D.devRating, D.devReviewCount, D.explanatoryRating, D.explanatoryReviewCount);
    }

    /** 분야 코드가 목록 중 하나인 개발자. 목록이 없으면 조건을 걸지 않습니다. */
    private static BooleanExpression fieldIn(Collection<Integer> fieldCodeIds) {
        return fieldCodeIds == null || fieldCodeIds.isEmpty() ? null : P.fieldCodeId.in(fieldCodeIds);
    }

    /** 이름 검색에 맞은 사용자의 개발자. 목록이 없으면 조건을 걸지 않습니다. */
    private static BooleanExpression userIn(Collection<Integer> userIds) {
        return userIds == null ? null : D.userId.in(userIds);
    }

    /** 이 개발자가 등록한 살아 있는 가격표 전체의 최저가. 가격표가 없으면 null이라 정렬에서 뒤로 갑니다. */
    private static JPQLQuery<Long> lowestPrice() {
        return JPAExpressions.select(PC.price.min())
                .from(PC)
                .where(PC.developerId.eq(D.id), PC.deleted.isFalse());
    }

    /**
     * Spring Data 정렬을 QueryDSL 정렬로 바꿉니다. 정렬이 없으면 id 오름차순이고, 있으면 마지막에 id 오름차순을 붙여 같은 값
     * 사이에서 페이지 경계가 흔들리지 않게 합니다. 평점·가격이 없는 개발자는 어느 정렬에서도 뒤로 갑니다.
     *
     * @throws IllegalArgumentException 지원하지 않는 속성이 왔을 때
     */
    private static OrderSpecifier<?>[] orderSpecifiers(Sort sort) {
        List<OrderSpecifier<?>> specifiers = new ArrayList<>();
        for (Sort.Order order : sort) {
            specifiers.add(orderSpecifier(order));
        }
        specifiers.add(D.id.asc());
        return specifiers.toArray(OrderSpecifier<?>[]::new);
    }

    private static OrderSpecifier<?> orderSpecifier(Sort.Order order) {
        Order direction = order.isAscending() ? Order.ASC : Order.DESC;
        return switch (order.getProperty()) {
            case DeveloperSort.PROPERTY_DEV_RATING ->
                    new OrderSpecifier<>(direction, D.devRating, OrderSpecifier.NullHandling.NullsLast);
            case DeveloperSort.PROPERTY_EXPLANATORY_RATING ->
                    new OrderSpecifier<>(direction, D.explanatoryRating, OrderSpecifier.NullHandling.NullsLast);
            case DeveloperSort.PROPERTY_PRICE ->
                    new OrderSpecifier<>(direction, lowestPrice(), OrderSpecifier.NullHandling.NullsLast);
            default -> throw new IllegalArgumentException("Unsupported sort property: " + order.getProperty());
        };
    }
}
