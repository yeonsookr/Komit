package com.a501.service.domain.consulting.dto.response;

import com.a501.service.domain.consulting.dto.SummaryContent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 세션이 끝난 뒤 만들어지는 요약.
 *
 * @param sessionId 컨설팅 세션
 * @param discussed 논의한 것. 요청서에는 반영되지 않는다
 * @param decided 결정된 것
 * @param nextActions 다음에 할 것
 * @param changes 요청서에 반영할 수 있는 변경 후보
 * @param updatedAt 요약을 마지막으로 고친 시각
 */
@Schema(description = "세션 요약")
public record SessionSummaryResponse(
        Integer sessionId,
        List<SummaryContent.Item> discussed,
        List<SummaryContent.Item> decided,
        List<SummaryContent.Item> nextActions,
        List<SummaryContent.Change> changes,
        OffsetDateTime updatedAt) {

    public static SessionSummaryResponse of(
            Integer sessionId, SummaryContent content, OffsetDateTime updatedAt) {
        return new SessionSummaryResponse(
                sessionId,
                content.discussed(),
                content.decided(),
                content.nextActions(),
                content.changes(),
                updatedAt);
    }
}
