package com.a501.service.domain.defaults.repository;

import java.util.List;
import java.util.Optional;

import com.a501.service.domain.defaults.dto.request.DefaultSort;
import com.a501.service.domain.defaults.dto.response.DefaultResponse;
import com.a501.service.domain.defaults.entity.QDefault;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

/**
 * {@link DefaultRepositoryCustom}의 QueryDSL 구현. (BACKEND_CONVENTIONS 13.3 성능 규칙)
 *
 * <ul>
 *   <li>필요한 컬럼(id, message)만 {@code Projections.constructor}로 DTO에 직접 담습니다. 엔티티를 통째로 올리지 않습니다.</li>
 *   <li>{@code limit}·{@code offset}을 항상 적용하고, count 쿼리는 정렬을 뺀 별도 쿼리로 둡니다.</li>
 *   <li>count는 {@link PageableExecutionUtils}가 필요할 때만 실행합니다. 첫 페이지가 크기보다 적게 차면 count 없이 끝납니다.</li>
 *   <li>조건은 {@link BooleanExpression}을 돌려주는 메서드로 표현합니다. 조건이 늘어나도 {@code where}에 나열만 하면 됩니다.</li>
 * </ul>
 *
 * <p>{@code QDefault}는 빌드 시 annotation processor가 만드는 클래스라 저장소에 커밋되지 않습니다.
 * 기본 정적 인스턴스 이름은 예약어 충돌 때문에 {@code default1}이 되므로 읽기 쉬운 별칭 {@code d}를 직접 만듭니다.
 */
@RequiredArgsConstructor
public class DefaultRepositoryImpl implements DefaultRepositoryCustom {
    private static final QDefault DEFAULT = new QDefault("d");

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<DefaultResponse> findActivePage(Pageable pageable) {
        List<DefaultResponse> content = queryFactory
                .select(Projections.constructor(DefaultResponse.class, DEFAULT.id, DEFAULT.message))
                .from(DEFAULT)
                .where(notDeleted())
                .orderBy(orderSpecifiers(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(DEFAULT.count())
                .from(DEFAULT)
                .where(notDeleted());

        return PageableExecutionUtils.getPage(content, pageable,
                () -> Optional.ofNullable(countQuery.fetchOne()).orElse(0L));
    }

    /** 소프트 삭제되지 않은 행. 삭제 판정은 {@code is_deleted} 한 컬럼으로 합니다. */
    private static BooleanExpression notDeleted() {
        return DEFAULT.deleted.isFalse();
    }

    /**
     * Spring Data 정렬을 QueryDSL 정렬로 바꿉니다. 정렬이 없으면 페이지 경계가 흔들리지 않도록 id 오름차순을 씁니다.
     *
     * @throws IllegalArgumentException {@link DefaultSort#PROPERTY} 외의 속성이 왔을 때
     */
    private static OrderSpecifier<?>[] orderSpecifiers(Sort sort) {
        if (sort.isUnsorted()) {
            return new OrderSpecifier<?>[] {DEFAULT.id.asc()};
        }
        return sort.stream()
                .map(DefaultRepositoryImpl::orderSpecifier)
                .toArray(OrderSpecifier<?>[]::new);
    }

    private static OrderSpecifier<?> orderSpecifier(Sort.Order order) {
        if (!DefaultSort.PROPERTY.equals(order.getProperty())) {
            throw new IllegalArgumentException("Unsupported sort property: " + order.getProperty());
        }
        return order.isAscending() ? DEFAULT.id.asc() : DEFAULT.id.desc();
    }
}
