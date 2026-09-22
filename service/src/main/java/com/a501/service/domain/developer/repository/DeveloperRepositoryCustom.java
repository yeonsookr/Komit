package com.a501.service.domain.developer.repository;

import com.a501.service.domain.developer.dto.DeveloperSearchCondition;
import com.a501.service.domain.developer.dto.DeveloperSearchRow;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 공개 개발자 목록·상세를 위한 QueryDSL 조회 선언. 구현은 {@link DeveloperRepositoryImpl}이며 Spring Data가 이름 규칙({@code *Impl})으로
 * 이어 붙입니다.
 *
 * <p>모든 조회는 "살아 있는 개발자 행에 살아 있는 프로필이 있는" 개발자만 봅니다. 프로필을 저장한 순간 개발자 찾기에 나오고 따로
 * 감출 수 없다는 목 명세를 그대로 옮긴 조건입니다.
 */
public interface DeveloperRepositoryCustom {

    /**
     * 조건에 맞는 개발자를 페이지로 조회합니다.
     *
     * @param condition 분야·사용자 식별자 조건. null 항목은 조건을 걸지 않습니다.
     * @param pageable  0부터 시작하는 페이지 번호, 크기, 정렬. 정렬 속성은 {@code devRating}·{@code explanatoryRating}·{@code price}만
     *                  허용하며, 정렬이 없으면 id 오름차순입니다.
     * @return 항목과 전체 건수. 마지막 페이지를 넘어선 요청이면 항목은 비어 있고 전체 건수는 채워집니다.
     * @throws org.springframework.dao.InvalidDataAccessApiUsageException 지원하지 않는 정렬 속성이 왔을 때. 요청 검증은 컨트롤러가
     *         끝냈으므로 여기까지 오면 코드 오류입니다.
     */
    Page<DeveloperSearchRow> searchPage(DeveloperSearchCondition condition, Pageable pageable);

    /**
     * 조건에 맞는 개발자 식별자 전부를 id 오름차순으로 돌려줍니다. DB 컬럼이 아닌 값(마친 계약 수)으로 정렬해야 할 때 서비스가
     * 이 목록을 정렬·페이지한 뒤 {@link #findRows(Collection)}로 행을 가져옵니다.
     */
    List<Integer> searchIds(DeveloperSearchCondition condition);

    /** 식별자에 해당하는 개발자 행. 순서는 보장하지 않으며 없거나 삭제되었거나 프로필이 없는 식별자는 결과에서 빠집니다. */
    List<DeveloperSearchRow> findRows(Collection<Integer> developerIds);

    /** 조건 없는 전체 개발자 수. 목 명세 {@code totalDevelopers}입니다. */
    long countActive();

    /**
     * 랜딩에 노출할 개발자. 개발력 평점이 높은 순이고 평점이 없으면 뒤로, 같으면 리뷰가 많은 순, 그다음 id 오름차순입니다.
     *
     * @param size 내려받을 수. 컨트롤러가 1~10으로 검증합니다.
     */
    List<DeveloperSearchRow> findFeatured(int size);
}
