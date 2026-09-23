package com.a501.service.domain.recommendation.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.recommendation.RecommendationErrorCode;
import com.a501.service.domain.recommendation.dto.request.CreateConsultationRequest;
import com.a501.service.domain.recommendation.dto.response.RecommendationBatchResponse;
import com.a501.service.domain.recommendation.entity.RecommendationBatch;
import com.a501.service.domain.recommendation.entity.RecommendationItem;
import com.a501.service.domain.recommendation.repository.RecommendationBatchRepository;
import com.a501.service.domain.recommendation.repository.RecommendationItemRepository;
import com.a501.service.domain.room.dto.InquiryTarget;
import com.a501.service.domain.room.dto.response.RoomListResponse;
import com.a501.service.domain.room.service.RoomService;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 만들어 둔 추천 회차를 다시 열어본다. */
@Service
@RequiredArgsConstructor
public class RecommendationQueryService {
    private final RecommendationBatchRepository batchRepository;
    private final RecommendationItemRepository itemRepository;
    private final RecommendationBatchAssembler batchAssembler;
    private final RoomService roomService;

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

    /**
     * 고른 개발자들에게 상담을 요청한다. 개발자마다 무료 상담방이 하나씩 열린다.
     *
     * <p><b>개발자의 수락을 기다리지 않는다.</b> 요청하면 그 자리에서 방이 열린다.
     *
     * <p><b>몇 번을 보내도 방은 개발자마다 하나다.</b> 이미 방이 있는 개발자는 그 방을 그대로 응답에 담는다.
     * 이미 요청했다는 이유로 막지 않으므로 여러 명 중 한 명이 걸려 나머지가 못 나가는 일도 없다.
     *
     * @throws BusinessException {@link RecommendationErrorCode#RECOMMENDATION_NOT_FOUND} 없거나 남의 회차일 때,
     *                           {@link CommonErrorCode#INVALID_REQUEST} 이 회차의 항목이 아닌 값이 섞였을 때
     */
    @Transactional
    public RoomListResponse requestConsultations(
            Integer batchId, Integer userId, CreateConsultationRequest request) {
        RecommendationBatch batch = batchRepository
                .findByIdAndDeletedFalse(batchId)
                .filter(found -> found.getRequesterUserId().equals(userId))
                .orElseThrow(
                        () -> new BusinessException(RecommendationErrorCode.RECOMMENDATION_NOT_FOUND));

        Map<Integer, RecommendationItem> items = itemRepository
                .findByBatchIdAndDeletedFalseOrderByRankAsc(batchId)
                .stream()
                .collect(Collectors.toMap(RecommendationItem::getId, item -> item));
        // 고르지 않은 개발자에게 요청이 나가면 안 되므로 조용히 버리지 않고 막는다.
        if (!items.keySet().containsAll(request.recommendationItemIds())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        // 같은 항목을 거듭 보내도 방은 하나다. 보낸 순서는 지킨다.
        List<RecommendationItem> targets = new LinkedHashSet<>(request.recommendationItemIds())
                .stream()
                .map(items::get)
                .toList();

        RoomListResponse rooms = roomService.openInquiries(
                userId,
                batch.getProjectId(),
                targets.stream()
                        .map(item -> new InquiryTarget(item.getId(), item.getDeveloperId()))
                        .toList());

        // 방은 보낸 순서 그대로 돌아온다. 이미 있던 방은 다른 회차에서 열렸을 수 있어
        // recommendationItemId 로 맞추면 이번 항목을 찾지 못한다.
        LocalDateTime requestedAt = LocalDateTime.now();
        for (int index = 0; index < targets.size(); index++) {
            RecommendationItem item = targets.get(index);
            if (!item.isRequested()) {
                item.requestConsultation(rooms.content().get(index).roomId(), requestedAt);
            }
        }
        return rooms;
    }
}
