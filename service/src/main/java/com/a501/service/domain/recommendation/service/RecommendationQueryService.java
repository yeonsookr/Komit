package com.a501.service.domain.recommendation.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.recommendation.RecommendationErrorCode;
import com.a501.service.domain.recommendation.dto.response.RecommendationBatchResponse;
import com.a501.service.domain.recommendation.entity.RecommendationBatch;
import com.a501.service.domain.recommendation.repository.RecommendationBatchRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 만들어 둔 추천 회차를 다시 열어본다. */
@Service
@RequiredArgsConstructor
public class RecommendationQueryService {
    private final RecommendationBatchRepository batchRepository;
    private final RecommendationBatchAssembler batchAssembler;

    /**
     * 추천 회차 하나를 순위대로 읽는다.
     *
     * <p><b>이 조회가 회차를 '열어본 것'으로 표시한다.</b> 그 표시가 있어야 추천 받기를 다시 불렀을 때 새 회차가 만들어진다. 표시가 없는 동안에는 추천 받기가 같은
     * 회차를 계속 돌려주므로, 이 API 를 부르지 않으면 두 번째 회차가 영영 생기지 않는다.
     *
     * <p>조회인데 기록을 남기지만 몇 번을 불러도 결과는 같다. 열람 시각은 처음 한 번만 찍힌다.
     *
     * @throws BusinessException {@link RecommendationErrorCode#RECOMMENDATION_NOT_FOUND} 없거나 남의 회차일 때
     */
    @Transactional
    public RecommendationBatchResponse get(Integer batchId, Integer userId) {
        RecommendationBatch batch = batchRepository
                .findByIdAndDeletedFalse(batchId)
                // 남의 회차는 없는 것과 같게 다룬다. 존재를 알리지 않는다. (BACKEND_CONVENTIONS 12.1)
                .filter(found -> found.getRequesterUserId().equals(userId))
                .orElseThrow(
                        () -> new BusinessException(RecommendationErrorCode.RECOMMENDATION_NOT_FOUND));

        batch.markViewed(LocalDateTime.now());
        return batchAssembler.assemble(batch, userId);
    }
}
