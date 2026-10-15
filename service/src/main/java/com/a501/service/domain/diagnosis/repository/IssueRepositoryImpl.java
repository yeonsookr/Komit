package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.Issue;
import com.a501.service.domain.diagnosis.entity.QIssue;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

/**
 * {@link IssueRepositoryCustom} 의 QueryDSL 구현. (BACKEND_CONVENTIONS 13.3)
 *
 * <p>거르는 조건이 요청마다 달라 {@link BooleanExpression} 으로 표현하고, 비어 있는 조건은 null 을 돌려 {@code where} 에서 빠지게 한다.
 *
 * <p>순서는 저장한 순서다. 올릴 때 의뢰 내용과 이어지는 결함을 앞에 두었으므로 그대로 내보내면 진단서가 그것을 위에 싣는다. (R-SCAN-08)
 */
@RequiredArgsConstructor
public class IssueRepositoryImpl implements IssueRepositoryCustom {
    private static final QIssue ISSUE = new QIssue("i");

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Issue> findPage(
            Integer inspectionId,
            String status,
            Boolean input,
            boolean requestOnly,
            Pageable pageable) {
        List<Issue> content = queryFactory
                .selectFrom(ISSUE)
                .where(inInspection(inspectionId), hasStatus(status), isInput(input), inRequest(requestOnly), notContract(), notDeleted())
                .orderBy(ISSUE.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(ISSUE.count())
                .from(ISSUE)
                .where(inInspection(inspectionId), hasStatus(status), isInput(input), inRequest(requestOnly), notContract(), notDeleted());

        return PageableExecutionUtils.getPage(
                content, pageable, () -> Optional.ofNullable(countQuery.fetchOne()).orElse(0L));
    }

    private static BooleanExpression inInspection(Integer inspectionId) {
        return ISSUE.inspectionId.eq(inspectionId);
    }

    private static BooleanExpression hasStatus(String status) {
        return status == null ? null : ISSUE.status.eq(status);
    }

    /**
     * 요청서에 담긴 것. 의뢰인이 고른 것과 이미 계약에 들어간 것이다.
     *
     * <p>고르지 않은 것은 의뢰인이 보여주지 않기로 한 것이므로 요청을 받은 개발자에게도 내보내지
     * 않는다.
     */
    private static BooleanExpression inRequest(boolean requestOnly) {
        return requestOnly ? ISSUE.status.in(Issue.REQUEST_STATUSES) : null;
    }

    private static BooleanExpression isInput(Boolean input) {
        return input == null ? null : ISSUE.input.eq(input);
    }

    /** 계약서에서 만든 요구사항. 요청서와 진단 목록에 섞지 않는다. 계약서 화면은 이 목록을 쓰지 않는다. */
    private static BooleanExpression notContract() {
        return ISSUE.contract.isFalse();
    }

    private static BooleanExpression notDeleted() {
        return ISSUE.deleted.isFalse();
    }
}
