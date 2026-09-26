package com.a501.service.domain.recommendation.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.developer.service.DeveloperLookupService;
import com.a501.service.domain.project.service.ProjectService;
import com.a501.service.domain.recommendation.RecommendationErrorCode;
import com.a501.service.domain.recommendation.dto.request.CreateDirectConsultationRequest;
import com.a501.service.domain.room.dto.InquiryTarget;
import com.a501.service.domain.room.dto.response.RoomListResponse;
import com.a501.service.domain.room.service.RoomService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 추천을 거치지 않고 직접 찾은 개발자에게 상담을 요청한다. */
@Service
@RequiredArgsConstructor
public class DirectConsultationService {
    private final ProjectService projectService;
    private final DeveloperLookupService developerLookupService;
    private final RoomService roomService;

    /**
     * 고른 개발자마다 무료 상담방을 연다. 추천 회차를 만들지 않으므로 방의 추천 항목은 비어 있다.
     *
     * <p>이미 방이 있는 개발자는 그 방을 그대로 담는다. 같은 개발자를 거듭 보내도 방은 하나다.
     *
     * @throws BusinessException {@link RecommendationErrorCode#PROJECT_NOT_FOUND} 없거나 남의 프로젝트일 때,
     *                           {@link CommonErrorCode#INVALID_REQUEST} 없는 개발자가 섞였거나 자기 자신에게 요청했을 때
     */
    @Transactional
    public RoomListResponse request(
            Integer projectId, Integer userId, CreateDirectConsultationRequest request) {
        projectService
                .findOwned(projectId, userId)
                .orElseThrow(() -> new BusinessException(RecommendationErrorCode.PROJECT_NOT_FOUND));

        // 보낸 순서는 지키고 겹친 것만 뺀다.
        List<Integer> developerIds = List.copyOf(new LinkedHashSet<>(request.developerIds()));
        Map<Integer, Integer> userIds = developerLookupService.findUserIds(developerIds);
        // 개발자이면서 의뢰인인 사용자는 자기 프로필에서도 이 버튼을 누를 수 있다.
        if (userIds.size() != developerIds.size() || userIds.containsValue(userId)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        return roomService.openInquiries(
                userId,
                projectId,
                developerIds.stream().map(developerId -> new InquiryTarget(null, developerId)).toList());
    }
}
