package com.a501.service.domain.developer.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.developer.DeveloperErrorCode;
import com.a501.service.domain.developer.dto.DeveloperSearchCondition;
import com.a501.service.domain.developer.dto.DeveloperSearchRow;
import com.a501.service.domain.developer.dto.request.DeveloperSort;
import com.a501.service.domain.developer.dto.request.SearchMode;
import com.a501.service.domain.developer.dto.response.DeveloperDetailResponse;
import com.a501.service.domain.developer.dto.response.DeveloperListResponse;
import com.a501.service.domain.developer.dto.response.DeveloperSummaryResponse;
import com.a501.service.domain.developer.repository.DeveloperRepository;
import com.a501.service.domain.review.dto.response.ReviewListResponse;
import com.a501.service.domain.review.service.ReviewQueryService;
import com.a501.service.domain.user.service.UserService;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 남에게 보이는 개발자 조회. 개발자 찾기({@code GET /api/developers}), 프로필({@code GET /api/developers/{developerId}}),
 * 받은 리뷰({@code GET /api/developers/{developerId}/reviews}), 랜딩의 추천 개발자가 씁니다. 모두 로그인 없이 부를 수 있습니다.
 *
 * <p><b>검색.</b> 이름 검색은 사용자 도메인에 이름으로 물어 받은 사용자 식별자를 조건으로 넘깁니다. 맞는 사용자가 없으면 DB를 부르지
 * 않고 빈 목록을 돌려줍니다. 문제 검색({@code problem})은 문장의 뜻을 읽어야 해서 목과 같이 거르지 않고 전체를 줍니다.
 * AI 서버가 붙으면 그 결과로 바꿉니다.
 *
 * <p><b>정렬.</b> 평점·비용은 개발자 도메인의 컬럼이라 DB에서 정렬·페이지합니다. 마친 계약 수는 방·계약 도메인의 집계이고
 * 개발자 테이블에 그 값이 없어, 조건에 맞는 식별자 전부를 받아 계약 수로 정렬한 뒤 그 페이지의 행만 다시 읽습니다.
 * 식별자만 메모리에 올리므로 지금 규모에서는 문제없지만, 개발자가 크게 늘면 ERD에 집계 컬럼을 두고 DB 정렬로 바꾸는 것이 맞습니다.
 *
 * <p><b>404.</b> 개발자 행이나 프로필이 없거나 삭제되었거나 사용자가 탈퇴했으면 {@code DEVELOPER_NOT_FOUND}입니다. 목이
 * {@code developerId}=404로 재현하는 사례가 실서버에서는 이 조건입니다. 공개 프로필이라 존재를 감출 이유가 없지만, 없음과 탈퇴를
 * 구분해 알려주지는 않습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeveloperQueryService {
    private final DeveloperRepository developerRepository;
    private final DeveloperSummaryAssembler assembler;
    private final UserService userService;
    private final CareerService careerService;
    private final PortfolioItemService portfolioItemService;
    private final PriceCatalogService priceCatalogService;
    private final ReviewQueryService reviewQueryService;

    /**
     * 개발자 찾기.
     *
     * @param searchMode   검색 방식
     * @param keyword      검색어. 비었으면 전체에서 고릅니다.
     * @param fieldCodeIds 분야 코드 식별자. 비었으면 분야로 거르지 않습니다. 아는 분야가 아닌 값은 아무도 맞지 않아 조용히 무시됩니다.
     * @param sort         정렬 기준
     * @param page         1부터 시작하는 페이지 번호
     * @param size         한 페이지 항목 수
     */
    public DeveloperListResponse search(
            SearchMode searchMode, String keyword, List<Integer> fieldCodeIds, DeveloperSort sort, int page, int size) {
        long totalDevelopers = developerRepository.countActive();

        Collection<Integer> userIds = null;
        String name = keyword == null ? "" : keyword.strip();
        if (searchMode == SearchMode.NAME && !name.isEmpty()) {
            userIds = userService.findActiveUserIdsByName(name);
            if (userIds.isEmpty()) {
                return DeveloperListResponse.empty(page, size, totalDevelopers);
            }
        }
        DeveloperSearchCondition condition = new DeveloperSearchCondition(
                fieldCodeIds == null || fieldCodeIds.isEmpty() ? null : fieldCodeIds, userIds);

        if (sort == DeveloperSort.CONTRACT_DESC) {
            return searchByContractCount(condition, page, size, totalDevelopers);
        }
        Pageable pageable = PageRequest.of(page - 1, size, sort.toSort());
        var found = developerRepository.searchPage(condition, pageable);
        List<DeveloperSummaryResponse> content = assembler.assemble(found.getContent());
        return DeveloperListResponse.of(new PageImpl<>(content, pageable, found.getTotalElements()), page, totalDevelopers);
    }

    /** 랜딩의 추천 개발자. 개발력 평점이 높은 순입니다. */
    public List<DeveloperSummaryResponse> findFeatured(int size) {
        return assembler.assemble(developerRepository.findFeatured(size));
    }

    /**
     * 공개 프로필 상세.
     *
     * @throws BusinessException {@link DeveloperErrorCode#DEVELOPER_NOT_FOUND} 없거나 탈퇴한 개발자
     */
    public DeveloperDetailResponse getDetail(Integer developerId) {
        DeveloperSearchRow row = requireVisible(developerId);
        DeveloperSummaryResponse summary = assembler.assemble(List.of(row)).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(DeveloperErrorCode.DEVELOPER_NOT_FOUND));
        return DeveloperDetailResponse.of(
                summary,
                row,
                careerService.listPublic(developerId),
                portfolioItemService.listPublic(developerId),
                priceCatalogService.list(developerId));
    }

    /**
     * 개발자가 받은 리뷰. 최신순이며 {@code page}는 1부터입니다.
     *
     * @throws BusinessException {@link DeveloperErrorCode#DEVELOPER_NOT_FOUND} 없거나 탈퇴한 개발자
     */
    public ReviewListResponse getReviews(Integer developerId, int page, int size) {
        requireVisible(developerId);
        return reviewQueryService.findPageByDeveloper(developerId, page, size);
    }

    /** 마친 계약 많은 순. 계약 수는 다른 도메인의 집계라 식별자를 모두 받아 정렬한 뒤 그 페이지의 행만 읽습니다. */
    private DeveloperListResponse searchByContractCount(
            DeveloperSearchCondition condition, int page, int size, long totalDevelopers) {
        List<Integer> ids = developerRepository.searchIds(condition);
        Map<Integer, Long> counts = assembler.countCompletedContracts(ids);
        List<Integer> ordered = ids.stream()
                .sorted(Comparator.comparingLong((Integer id) -> counts.getOrDefault(id, 0L)).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();

        int from = Math.min((page - 1) * size, ordered.size());
        List<Integer> pageIds = ordered.subList(from, Math.min(from + size, ordered.size()));
        Map<Integer, DeveloperSearchRow> rows = developerRepository.findRows(pageIds).stream()
                .collect(Collectors.toMap(DeveloperSearchRow::developerId, Function.identity()));
        List<DeveloperSearchRow> orderedRows = pageIds.stream().map(rows::get).filter(java.util.Objects::nonNull).toList();

        Pageable pageable = PageRequest.of(page - 1, size);
        return DeveloperListResponse.of(
                new PageImpl<>(assembler.assemble(orderedRows, counts), pageable, ordered.size()), page, totalDevelopers);
    }

    private DeveloperSearchRow requireVisible(Integer developerId) {
        return developerRepository.findRows(List.of(developerId)).stream()
                .findFirst()
                .filter(row -> userService.isActive(row.userId()))
                .orElseThrow(() -> new BusinessException(DeveloperErrorCode.DEVELOPER_NOT_FOUND));
    }
}
