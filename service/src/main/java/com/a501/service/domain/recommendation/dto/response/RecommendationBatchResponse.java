package com.a501.service.domain.recommendation.dto.response;

import com.a501.service.domain.developer.dto.response.DeveloperSummaryResponse;
import com.a501.service.domain.recommendation.entity.RecommendationBatch;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "추천 회차 하나. 한 번의 상담 요청으로 뽑힌 개발자 묶음이다.")
public record RecommendationBatchResponse(
        @Schema(description = "추천 회차 식별자.", example = "61") Integer batchId,
        @Schema(description = "이 프로젝트에서 몇 번째 추천인지. 다시 찾으면 1씩 오른다.", example = "1")
        Integer sequenceNo,
        @Schema(description = "추천 대상 프로젝트 식별자.", example = "8") Integer projectId,
        @Schema(description = "프로젝트 이름. 화면 머리에 쓴다.", example = "수제 비누 가게") String projectName,
        @Schema(description = "이 추천을 만든 시점의 진단 회차. 재진단한 뒤 옛 추천을 열었을 때 무엇을 근거로 뽑았는지 알려준다.", example = "2")
        Integer diagnosisVersion,
        @Schema(description = "점수를 매긴 개발자 수. 순위를 매기기 전 모집단이다.", example = "128")
        Integer candidateCount,
        @Schema(description = "추천된 개발자. 순위대로 내려간다.")
        List<RecommendationItemResponse> items) {

    public static RecommendationBatchResponse of(
            RecommendationBatch batch, String projectName, List<RecommendationItemResponse> items) {
        return new RecommendationBatchResponse(
                batch.getId(),
                batch.getSequenceNo().intValue(),
                batch.getProjectId(),
                projectName,
                batch.getDiagnosisVersion() == null ? null : batch.getDiagnosisVersion().intValue(),
                batch.getCandidateCount().intValue(),
                items);
    }

    @Schema(description = "추천된 개발자 한 명.")
    public record RecommendationItemResponse(
            @Schema(description = "추천 항목 식별자. 상담을 요청할 때 이 값을 보낸다.", example = "501")
            Integer recommendationItemId,
            @Schema(description = "노출 순위. 1부터 시작한다.", example = "1") Integer rank,
            @Schema(description = "추천된 개발자. 언제나 지금 값이다. 카드에 적힌 금액과 상담에서 듣는 금액이 달라지면 안 된다.")
            DeveloperSummaryResponse developer,
            @Schema(description = "상담을 요청해 열린 방 식별자. 아직 요청하지 않았으면 null 이다. 이 값 하나가 요청 여부를 말한다.")
            Integer roomId) {}
}
