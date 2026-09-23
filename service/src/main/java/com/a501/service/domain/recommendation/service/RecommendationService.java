package com.a501.service.domain.recommendation.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.developer.dto.MatchCandidate;
import com.a501.service.domain.developer.service.DeveloperLookupService;
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
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 고른 요구사항을 근거로 맞는 개발자를 찾아 추천 회차를 만든다.
 *
 * <p>직접 찾아 고른 개발자는 이 길을 거치지 않는다. 그 길은 프로젝트 상담 요청이 맡고 회차 없이 방이 바로 열린다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {
    private final RecommendationBatchRepository batchRepository;
    private final RecommendationItemRepository itemRepository;
    private final ProjectService projectService;
    private final IssueQueryService issueQueryService;
    private final DeveloperLookupService developerLookupService;
    private final RecommendationBatchAssembler batchAssembler;
    private final ObjectMapper objectMapper;

    /**
     * 추천 회차를 만든다.
     *
     * <p><b>아직 열어보지 않은 회차가 있으면 새로 만들지 않는다.</b> 직전 회차를 한 번도 열지 않았다면 그 회차를 그대로 돌려준다. 새로고침하거나 버튼을 두 번 눌러 빈
     * 회차가 쌓이는 것을 막는다.
     *
     * @throws BusinessException {@link RecommendationErrorCode#PROJECT_NOT_FOUND} 없거나 남의 프로젝트일 때
     */
    @Transactional
    public RecommendationBatchResponse create(
            Integer projectId, Integer userId, CreateRecommendationRequest request) {
        ProjectSummary project = projectService
                .findOwned(projectId, userId)
                .orElseThrow(() -> new BusinessException(RecommendationErrorCode.PROJECT_NOT_FOUND));

        // 고르지 않은 것이 요청서에 실리면 계약까지 따라가므로, 조용히 버리지 않고 막는다.
        DiagnosisMatchInput input =
                issueQueryService.readMatchInput(request.diagnosisId(), userId, request.issueIds());

        RecommendationBatch previous = batchRepository
                .findFirstByProjectIdAndDeletedFalseOrderBySequenceNoDesc(projectId)
                .orElse(null);
        if (previous != null && !previous.isViewed()) {
            return batchAssembler.assemble(previous, project.name());
        }

        List<MatchCandidate> candidates = developerLookupService.findMatchCandidates();
        RecommendationBatch batch = batchRepository.save(RecommendationBatch.of(
                projectId,
                userId,
                input.diagnosisId(),
                input.diagnosisVersion(),
                previous,
                MatchScore.VERSION,
                writeJson(input.snapshot()),
                candidates.size()));

        saveItems(batch, input, candidates);
        return batchAssembler.assemble(batch, project.name());
    }

    private void saveItems(
            RecommendationBatch batch, DiagnosisMatchInput input, List<MatchCandidate> candidates) {
        record Ranked(MatchCandidate candidate, MatchScore.Scored scored) {}

        List<Ranked> ranked = candidates.stream()
                .map(candidate -> new Ranked(
                        candidate,
                        MatchScore.of(
                                input.techCodeIds(),
                                new MatchScore.Candidate(
                                        candidate.developerId(),
                                        candidate.techCodeIds(),
                                        candidate.devRating(),
                                        candidate.explanatoryRating(),
                                        candidate.reviewCount(),
                                        candidate.yearsOfExperience(),
                                        candidate.hasVerifiedCareer()))))
                // 점수가 같으면 리뷰가 많은 쪽, 그래도 같으면 식별자 순. 새로고침해도 순서가 흔들리지 않게 한다.
                .sorted(Comparator.comparingDouble((Ranked row) -> row.scored().total())
                        .thenComparingInt(row -> row.candidate().reviewCount())
                        .reversed()
                        .thenComparingInt(row -> row.candidate().developerId()))
                .toList();

        int rank = 1;
        for (Ranked row : ranked) {
            itemRepository.save(RecommendationItem.of(
                    batch.getId(),
                    row.candidate().developerId(),
                    rank++,
                    BigDecimal.valueOf(row.scored().total()),
                    writeJson(row.scored().breakdown()),
                    writeJson(row.candidate())));
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
    }
}
