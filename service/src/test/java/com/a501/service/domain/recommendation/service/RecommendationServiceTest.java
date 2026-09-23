package com.a501.service.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.developer.dto.MatchCandidate;
import com.a501.service.domain.developer.dto.response.DeveloperSummaryResponse;
import com.a501.service.domain.developer.service.DeveloperLookupService;
import com.a501.service.domain.developer.service.DeveloperSummaryAssembler;
import com.a501.service.domain.diagnosis.service.DiagnosisMatchInput;
import com.a501.service.domain.diagnosis.service.IssueQueryService;
import com.a501.service.domain.project.dto.ProjectSummary;
import com.a501.service.domain.project.service.ProjectService;
import com.a501.service.domain.recommendation.RecommendationErrorCode;
import com.a501.service.domain.recommendation.dto.request.CreateRecommendationRequest;
import com.a501.service.domain.recommendation.dto.response.RecommendationBatchResponse;
import com.a501.service.domain.recommendation.entity.RecommendationBatch;
import com.a501.service.domain.recommendation.entity.RecommendationItem;
import com.a501.service.domain.recommendation.repository.RecommendationBatchRepository;
import com.a501.service.domain.recommendation.repository.RecommendationItemRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 추천 회차를 만들고 다시 열어보는 규칙을 DB 없이 검증합니다. 순위 정렬, 미열람 회차 재사용, 회차 번호와 계기, 열람 표시, 404 조건이 대상입니다.
 *
 * <p>점수 계산 자체는 {@link MatchScoreTest}가 맡습니다. 여기서는 계산한 점수를 어떤 순서로 저장하는지만 봅니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceTest {
    private static final Integer PROJECT_ID = 8;
    private static final Integer USER_ID = 9001;
    private static final Integer DIAGNOSIS_ID = 31;
    private static final Set<Integer> PROJECT_TECHS = Set.of(21, 23, 24);

    @Mock
    private RecommendationBatchRepository batchRepository;
    @Mock
    private RecommendationItemRepository itemRepository;
    @Mock
    private ProjectService projectService;
    @Mock
    private IssueQueryService issueQueryService;
    @Mock
    private DeveloperLookupService developerLookupService;
    @Mock
    private DeveloperSummaryAssembler developerSummaryAssembler;

    private RecommendationService service;
    private RecommendationQueryService queryService;
    private List<RecommendationItem> savedItems;

    private final CreateRecommendationRequest request =
            new CreateRecommendationRequest(DIAGNOSIS_ID, List.of(73, 74));

    @BeforeEach
    void setUp() {
        // 조립기는 로직이 얕아 가짜로 두지 않고 그대로 씁니다. 응답 모양까지 함께 보기 위해서입니다.
        RecommendationBatchAssembler batchAssembler = new RecommendationBatchAssembler(
                itemRepository, developerSummaryAssembler, projectService);
        service = new RecommendationService(
                batchRepository,
                itemRepository,
                projectService,
                issueQueryService,
                developerLookupService,
                batchAssembler,
                new ObjectMapper());
        queryService = new RecommendationQueryService(batchRepository, batchAssembler);

        when(projectService.findOwned(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(new ProjectSummary(
                        PROJECT_ID, "수제 비누 가게", "동네 비누 가게 주문 페이지", "커머스", List.of())));
        when(issueQueryService.readMatchInput(DIAGNOSIS_ID, USER_ID, request.issueIds()))
                .thenReturn(new DiagnosisMatchInput(
                        DIAGNOSIS_ID,
                        (short) 1,
                        request.issueIds(),
                        PROJECT_TECHS,
                        Map.of("diagnosisId", DIAGNOSIS_ID)));

        // 저장은 식별자를 붙여 돌려줍니다. 응답을 만들 때 회차 식별자와 순위를 읽기 때문입니다.
        savedItems = new ArrayList<>();
        when(batchRepository.save(any(RecommendationBatch.class))).thenAnswer(call -> {
            RecommendationBatch batch = call.getArgument(0);
            ReflectionTestUtils.setField(batch, "id", 61);
            return batch;
        });
        AtomicInteger itemId = new AtomicInteger(500);
        when(itemRepository.save(any(RecommendationItem.class))).thenAnswer(call -> {
            RecommendationItem item = call.getArgument(0);
            ReflectionTestUtils.setField(item, "id", itemId.incrementAndGet());
            savedItems.add(item);
            return item;
        });
        when(itemRepository.findByBatchIdAndDeletedFalseOrderByRankAsc(61))
                .thenAnswer(call -> savedItems);
        when(developerSummaryAssembler.assembleByIds(anyCollection())).thenAnswer(call -> {
            java.util.Collection<Integer> ids = call.getArgument(0);
            return ids.stream().map(RecommendationServiceTest::summary).toList();
        });
    }

    @Test
    @DisplayName("점수가 높은 개발자가 앞에 온다")
    void ranksByScore() {
        when(developerLookupService.findMatchCandidates()).thenReturn(List.of(
                candidate(3, Set.of(99), 5.0, 20),        // 기술이 하나도 안 맞는다
                candidate(1, PROJECT_TECHS, 5.0, 10),     // 기술 전부 + 최고 평점
                candidate(2, PROJECT_TECHS, 3.0, 5)));    // 기술 전부 + 낮은 평점

        RecommendationBatchResponse response = service.create(PROJECT_ID, USER_ID, request);

        assertThat(response.items()).extracting(item -> item.developer().developerId())
                .containsExactly(1, 2, 3);
        assertThat(response.items()).extracting(RecommendationBatchResponse.RecommendationItemResponse::rank)
                .containsExactly(1, 2, 3);
        assertThat(response.candidateCount()).isEqualTo(3);
        assertThat(response.sequenceNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("점수가 같으면 리뷰가 많은 쪽이 앞에 온다")
    void breaksTieByReviewCount() {
        when(developerLookupService.findMatchCandidates()).thenReturn(List.of(
                candidate(7, PROJECT_TECHS, 4.0, 3),
                candidate(6, PROJECT_TECHS, 4.0, 31)));

        RecommendationBatchResponse response = service.create(PROJECT_ID, USER_ID, request);

        assertThat(response.items()).extracting(item -> item.developer().developerId())
                .containsExactly(6, 7);
    }

    @Test
    @DisplayName("직전 회차를 아직 열어보지 않았으면 새 회차를 만들지 않는다")
    void reusesUnviewedBatch() {
        RecommendationBatch previous = batch(60, (short) 1, null);
        when(batchRepository.findFirstByProjectIdAndDeletedFalseOrderBySequenceNoDesc(PROJECT_ID))
                .thenReturn(Optional.of(previous));
        when(itemRepository.findByBatchIdAndDeletedFalseOrderByRankAsc(60)).thenReturn(List.of());

        RecommendationBatchResponse response = service.create(PROJECT_ID, USER_ID, request);

        assertThat(response.batchId()).isEqualTo(60);
        verify(batchRepository, never()).save(any());
        verify(developerLookupService, never()).findMatchCandidates();
    }

    @Test
    @DisplayName("직전 회차를 열어봤으면 회차 번호가 오르고 재매칭으로 남는다")
    void continuesSequenceAfterViewed() {
        when(batchRepository.findFirstByProjectIdAndDeletedFalseOrderBySequenceNoDesc(PROJECT_ID))
                .thenReturn(Optional.of(batch(60, (short) 2, LocalDateTime.now())));
        when(developerLookupService.findMatchCandidates())
                .thenReturn(List.of(candidate(1, PROJECT_TECHS, 4.0, 1)));

        RecommendationBatchResponse response = service.create(PROJECT_ID, USER_ID, request);

        assertThat(response.sequenceNo()).isEqualTo(3);
        assertThat(savedBatch().getParentBatchId()).isEqualTo(60);
        assertThat(savedBatch().getTriggerType()).isEqualTo("REMATCH");
    }

    @Test
    @DisplayName("첫 회차는 상담 요청으로 남는다")
    void firstBatchIsConsultRequest() {
        when(developerLookupService.findMatchCandidates())
                .thenReturn(List.of(candidate(1, PROJECT_TECHS, 4.0, 1)));

        service.create(PROJECT_ID, USER_ID, request);

        assertThat(savedBatch().getTriggerType()).isEqualTo("CONSULT_REQUEST");
        assertThat(savedBatch().getParentBatchId()).isNull();
        assertThat(savedBatch().getAlgorithmVersion()).isEqualTo(MatchScore.VERSION);
    }

    @Test
    @DisplayName("맞는 개발자가 없어도 회차는 남고 목록만 빈다")
    void keepsBatchWhenNoCandidate() {
        when(developerLookupService.findMatchCandidates()).thenReturn(List.of());

        RecommendationBatchResponse response = service.create(PROJECT_ID, USER_ID, request);

        assertThat(response.items()).isEmpty();
        assertThat(response.candidateCount()).isZero();
    }

    @Test
    @DisplayName("남의 프로젝트면 404다")
    void rejectsForeignProject() {
        when(projectService.findOwned(PROJECT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(PROJECT_ID, USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RecommendationErrorCode.PROJECT_NOT_FOUND);
        verify(batchRepository, never()).save(any());
    }

    @Test
    @DisplayName("이 진단에서 나오지 않은 요구사항이 섞이면 회차를 만들지 않는다")
    void rejectsForeignIssue() {
        when(issueQueryService.readMatchInput(DIAGNOSIS_ID, USER_ID, request.issueIds()))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> service.create(PROJECT_ID, USER_ID, request))
                .isInstanceOf(BusinessException.class);
        verify(batchRepository, never()).save(any());
    }

    @Test
    @DisplayName("회차를 처음 열면 열람 시각이 찍힌다")
    void marksViewedOnFirstOpen() {
        RecommendationBatch target = batch(61, (short) 1, null);
        when(batchRepository.findByIdAndDeletedFalse(61)).thenReturn(Optional.of(target));

        RecommendationBatchResponse response = queryService.get(61, USER_ID);

        assertThat(response.batchId()).isEqualTo(61);
        assertThat(target.isViewed()).isTrue();
    }

    @Test
    @DisplayName("다시 열어도 열람 시각은 처음 값 그대로다")
    void keepsFirstViewedAt() {
        LocalDateTime first = LocalDateTime.of(2026, 9, 20, 10, 0);
        RecommendationBatch target = batch(61, (short) 1, first);
        when(batchRepository.findByIdAndDeletedFalse(61)).thenReturn(Optional.of(target));

        queryService.get(61, USER_ID);

        assertThat(target.getViewedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("남의 회차는 없는 것과 같게 404다")
    void hidesForeignBatch() {
        when(batchRepository.findByIdAndDeletedFalse(61))
                .thenReturn(Optional.of(batch(61, (short) 1, null)));

        assertThatThrownBy(() -> queryService.get(61, 9999))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", RecommendationErrorCode.RECOMMENDATION_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 회차는 404다")
    void rejectsMissingBatch() {
        when(batchRepository.findByIdAndDeletedFalse(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.get(404, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", RecommendationErrorCode.RECOMMENDATION_NOT_FOUND);
    }

    @Test
    @DisplayName("열어본 뒤에 다시 추천 받으면 새 회차가 생긴다")
    void openingUnlocksNextBatch() {
        RecommendationBatch first = batch(60, (short) 1, null);
        when(batchRepository.findByIdAndDeletedFalse(60)).thenReturn(Optional.of(first));
        when(batchRepository.findFirstByProjectIdAndDeletedFalseOrderBySequenceNoDesc(PROJECT_ID))
                .thenReturn(Optional.of(first));
        when(developerLookupService.findMatchCandidates())
                .thenReturn(List.of(candidate(1, PROJECT_TECHS, 4.0, 1)));

        // 열어보기 전에는 같은 회차만 돌아온다.
        assertThat(service.create(PROJECT_ID, USER_ID, request).batchId()).isEqualTo(60);
        verify(batchRepository, never()).save(any());

        queryService.get(60, USER_ID);

        assertThat(service.create(PROJECT_ID, USER_ID, request).sequenceNo()).isEqualTo(2);
    }

    private RecommendationBatch savedBatch() {
        org.mockito.ArgumentCaptor<RecommendationBatch> captor =
                org.mockito.ArgumentCaptor.forClass(RecommendationBatch.class);
        verify(batchRepository).save(captor.capture());
        return captor.getValue();
    }

    private static RecommendationBatch batch(Integer id, Short sequenceNo, LocalDateTime viewedAt) {
        RecommendationBatch entity = RecommendationBatch.of(
                PROJECT_ID, USER_ID, DIAGNOSIS_ID, (short) 1, null, MatchScore.VERSION, "{}", 0);
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "sequenceNo", sequenceNo);
        if (viewedAt != null) {
            entity.markViewed(viewedAt);
        }
        return entity;
    }

    private static MatchCandidate candidate(
            Integer developerId, Set<Integer> techCodeIds, Double rating, int reviewCount) {
        return new MatchCandidate(
                developerId, techCodeIds, rating, rating, reviewCount, 5.0, true);
    }

    private static DeveloperSummaryResponse summary(Integer developerId) {
        return new DeveloperSummaryResponse(
                developerId, "개발자 " + developerId, null, null, null, null, null, false,
                null, null, List.of(), List.of(), 0);
    }
}
