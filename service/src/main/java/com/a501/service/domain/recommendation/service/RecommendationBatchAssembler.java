package com.a501.service.domain.recommendation.service;

import com.a501.service.domain.developer.dto.response.DeveloperSummaryResponse;
import com.a501.service.domain.developer.service.DeveloperSummaryAssembler;
import com.a501.service.domain.project.dto.ProjectSummary;
import com.a501.service.domain.project.service.ProjectService;
import com.a501.service.domain.recommendation.dto.response.RecommendationBatchResponse;
import com.a501.service.domain.recommendation.dto.response.RecommendationBatchResponse.RecommendationItemResponse;
import com.a501.service.domain.recommendation.entity.RecommendationBatch;
import com.a501.service.domain.recommendation.entity.RecommendationItem;
import com.a501.service.domain.recommendation.repository.RecommendationItemRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 추천 회차 하나를 화면에 나갈 모양으로 만든다. 추천을 만들 때와 다시 열어볼 때가 같은 모양이어야 하므로 한 곳에 둔다.
 *
 * <p>추천 시점 프로필은 회차에 남기지만 응답에는 지금 값을 싣는다. 카드에 적힌 금액과 상담에서 듣는 금액이 달라지면 안 된다.
 */
@Component
@RequiredArgsConstructor
public class RecommendationBatchAssembler {
    private final RecommendationItemRepository itemRepository;
    private final DeveloperSummaryAssembler developerSummaryAssembler;
    private final ProjectService projectService;

    /** 프로젝트 이름을 이미 읽어 둔 곳에서 쓴다. */
    public RecommendationBatchResponse assemble(RecommendationBatch batch, String projectName) {
        List<RecommendationItem> items =
                itemRepository.findByBatchIdAndDeletedFalseOrderByRankAsc(batch.getId());
        Map<Integer, DeveloperSummaryResponse> developers = developerSummaryAssembler
                .assembleByIds(items.stream().map(RecommendationItem::getDeveloperId).toList())
                .stream()
                .collect(Collectors.toMap(DeveloperSummaryResponse::developerId, summary -> summary));

        return RecommendationBatchResponse.of(
                batch,
                projectName,
                items.stream()
                        // 탈퇴하거나 지워진 개발자는 뺀다. 회차 기록에는 남지만 화면에는 내보내지 않는다.
                        .filter(item -> developers.containsKey(item.getDeveloperId()))
                        .map(item -> new RecommendationItemResponse(
                                item.getId(),
                                item.getRank().intValue(),
                                developers.get(item.getDeveloperId()),
                                item.getRoomId()))
                        .toList());
    }

    /** 회차만 있을 때. 프로젝트 이름을 따로 읽는다. */
    public RecommendationBatchResponse assemble(RecommendationBatch batch, Integer userId) {
        String projectName = projectService
                .findOwned(batch.getProjectId(), userId)
                .map(ProjectSummary::name)
                .orElse(null);
        return assemble(batch, projectName);
    }
}
