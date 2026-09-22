package com.a501.service.domain.developer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.developer.DeveloperErrorCode;
import com.a501.service.domain.developer.dto.DeveloperSearchCondition;
import com.a501.service.domain.developer.dto.DeveloperSearchRow;
import com.a501.service.domain.developer.dto.request.DeveloperSort;
import com.a501.service.domain.developer.dto.request.SearchMode;
import com.a501.service.domain.developer.dto.response.DeveloperListResponse;
import com.a501.service.domain.developer.dto.response.DeveloperSummaryResponse;
import com.a501.service.domain.developer.dto.response.RatingResponse;
import com.a501.service.domain.developer.repository.DeveloperRepository;
import com.a501.service.domain.review.dto.response.ReviewListResponse;
import com.a501.service.domain.review.service.ReviewQueryService;
import com.a501.service.domain.user.service.UserService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 서비스 계층의 규칙을 DB 없이 검증합니다. 이름 검색을 사용자 식별자 조건으로 바꾸는 것, 1-based → 0-based 페이지 변환, 정렬 전달,
 * 계약 많은 순의 메모리 정렬·페이지, 404 조건이 대상입니다.
 */
@ExtendWith(MockitoExtension.class)
class DeveloperQueryServiceTest {
    @Mock
    private DeveloperRepository developerRepository;
    @Mock
    private DeveloperSummaryAssembler assembler;
    @Mock
    private UserService userService;
    @Mock
    private CareerService careerService;
    @Mock
    private PortfolioItemService portfolioItemService;
    @Mock
    private PriceCatalogService priceCatalogService;
    @Mock
    private ReviewQueryService reviewQueryService;
    @InjectMocks
    private DeveloperQueryService service;

    private static DeveloperSearchRow row(int developerId) {
        return new DeveloperSearchRow(developerId, developerId + 100, "소개", 42, null, null, null, null, 4.4, 2, 4.9, 2);
    }

    private static DeveloperSummaryResponse summary(int developerId) {
        return new DeveloperSummaryResponse(developerId, "이름", null, null, "소개", null, null, false,
                RatingResponse.of(4.4, 2), RatingResponse.of(4.9, 2), List.of(), List.of(), 0);
    }

    @Test
    @DisplayName("평점 정렬은 1-based page를 0-based로 옮겨 DB에 넘기고 응답에는 요청한 번호와 전체 개발자 수를 담는다")
    void searchTranslatesPageAndSort() {
        when(developerRepository.countActive()).thenReturn(128L);
        Pageable expected = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "explanatoryRating"));
        // 두 번째 장(크기 5)에 한 명이 남았으니 전체는 6명이다.
        Page<DeveloperSearchRow> found = new PageImpl<>(List.of(row(3)), expected, 6);
        when(developerRepository.searchPage(any(), any())).thenReturn(found);
        when(assembler.assemble(List.of(row(3)))).thenReturn(List.of(summary(3)));

        DeveloperListResponse response = service.search(
                SearchMode.PROBLEM, "예약 버튼이 아무 반응 없어요", List.of(41, 42), DeveloperSort.EXPLANATORY_RATING_DESC, 2, 5);

        ArgumentCaptor<DeveloperSearchCondition> condition = ArgumentCaptor.forClass(DeveloperSearchCondition.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(developerRepository).searchPage(condition.capture(), pageable.capture());
        // problem 검색은 거르지 않으므로 사용자 조건이 없고, 분야만 조건으로 간다.
        assertThat(condition.getValue()).isEqualTo(new DeveloperSearchCondition(List.of(41, 42), null));
        assertThat(pageable.getValue()).isEqualTo(expected);
        assertThat(response).isEqualTo(new DeveloperListResponse(List.of(summary(3)), 2, 5, 6, 2, 128));
        verify(userService, never()).findActiveUserIdsByName(anyString());
    }

    @Test
    @DisplayName("이름 검색은 사용자 도메인에 물어 받은 식별자를 조건으로 넘기고, 맞는 사용자가 없으면 DB를 부르지 않는다")
    void nameSearchGoesThroughUserService() {
        when(developerRepository.countActive()).thenReturn(128L);
        when(userService.findActiveUserIdsByName("이준")).thenReturn(List.of(103));
        when(developerRepository.searchPage(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "devRating")), 0));
        when(assembler.assemble(List.of())).thenReturn(List.of());

        service.search(SearchMode.NAME, " 이준 ", null, DeveloperSort.DEV_RATING_DESC, 1, 20);

        ArgumentCaptor<DeveloperSearchCondition> condition = ArgumentCaptor.forClass(DeveloperSearchCondition.class);
        verify(developerRepository).searchPage(condition.capture(), any());
        assertThat(condition.getValue()).isEqualTo(new DeveloperSearchCondition(null, List.of(103)));

        when(userService.findActiveUserIdsByName("없는이름")).thenReturn(List.of());
        DeveloperListResponse empty = service.search(SearchMode.NAME, "없는이름", List.of(), DeveloperSort.DEV_RATING_DESC, 1, 20);

        assertThat(empty).isEqualTo(DeveloperListResponse.empty(1, 20, 128));
        // 두 번째 검색은 DB를 부르지 않았으므로 목록 조회는 첫 검색의 한 번뿐이다.
        verify(developerRepository, times(1)).searchPage(any(), any());
    }

    @Test
    @DisplayName("계약 많은 순은 조건에 맞는 식별자를 모두 받아 계약 수 내림차순·id 오름차순으로 정렬한 뒤 그 페이지 행만 읽는다")
    void contractSortIsAppliedInService() {
        when(developerRepository.countActive()).thenReturn(128L);
        when(developerRepository.searchIds(DeveloperSearchCondition.none())).thenReturn(List.of(1, 2, 3, 4, 5));
        when(assembler.countCompletedContracts(List.of(1, 2, 3, 4, 5))).thenReturn(Map.of(2, 9L, 4, 9L, 5, 1L));
        // 정렬 결과 [2, 4, 5, 1, 3] 중 두 번째 장(크기 2)은 [5, 1]. 저장소는 순서를 보장하지 않으므로 뒤집어 돌려준다.
        when(developerRepository.findRows(List.of(5, 1))).thenReturn(List.of(row(1), row(5)));
        Map<Integer, Long> counts = Map.of(2, 9L, 4, 9L, 5, 1L);
        when(assembler.assemble(List.of(row(5), row(1)), counts)).thenReturn(List.of(summary(5), summary(1)));

        DeveloperListResponse response = service.search(SearchMode.PROBLEM, null, null, DeveloperSort.CONTRACT_DESC, 2, 2);

        assertThat(response).isEqualTo(new DeveloperListResponse(List.of(summary(5), summary(1)), 2, 2, 5, 3, 128));
        verify(developerRepository, never()).searchPage(any(), any());
    }

    @Test
    @DisplayName("계약 많은 순에서 마지막 페이지를 넘어선 요청은 빈 content와 채워진 페이지 정보다")
    void contractSortBeyondLastPageIsEmpty() {
        when(developerRepository.countActive()).thenReturn(128L);
        when(developerRepository.searchIds(DeveloperSearchCondition.none())).thenReturn(List.of(1, 2));
        when(assembler.countCompletedContracts(List.of(1, 2))).thenReturn(Map.of());
        when(developerRepository.findRows(List.of())).thenReturn(List.of());
        when(assembler.assemble(List.of(), Map.of())).thenReturn(List.of());

        DeveloperListResponse response = service.search(SearchMode.PROBLEM, null, null, DeveloperSort.CONTRACT_DESC, 9, 20);

        assertThat(response).isEqualTo(new DeveloperListResponse(List.of(), 9, 20, 2, 1, 128));
    }

    @Test
    @DisplayName("프로필과 리뷰는 개발자 행이 없거나 사용자가 탈퇴했으면 DEVELOPER_NOT_FOUND다")
    void detailAndReviewsRequireVisibleDeveloper() {
        when(developerRepository.findRows(List.of(404))).thenReturn(List.of());
        when(developerRepository.findRows(List.of(7))).thenReturn(List.of(row(7)));
        when(userService.isActive(107)).thenReturn(false);

        for (int developerId : new int[] {404, 7}) {
            assertThatThrownBy(() -> service.getDetail(developerId))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(DeveloperErrorCode.DEVELOPER_NOT_FOUND));
            assertThatThrownBy(() -> service.getReviews(developerId, 1, 20))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(DeveloperErrorCode.DEVELOPER_NOT_FOUND));
        }
        verify(assembler, never()).assemble(anyList());
        verify(reviewQueryService, never()).findPageByDeveloper(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("리뷰 목록은 개발자가 보이면 리뷰 도메인의 페이지 조회를 그대로 돌려준다")
    void reviewsDelegateToReviewDomain() {
        when(developerRepository.findRows(List.of(3))).thenReturn(List.of(row(3)));
        when(userService.isActive(103)).thenReturn(true);
        ReviewListResponse expected = new ReviewListResponse(List.of(), 2, 3, 0, 0);
        when(reviewQueryService.findPageByDeveloper(3, 2, 3)).thenReturn(expected);

        assertThat(service.getReviews(3, 2, 3)).isSameAs(expected);
    }
}
