package com.a501.service.domain.consulting.dto.response;

import com.a501.service.domain.consulting.entity.ParticipationRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 세션 참가자 한 사람의 기록.
 *
 * <p><b>행이 있다는 것이 곧 동의했다는 뜻입니다</b> (`R-CON-17`). 그래서 목록에 이름이 없는
 * 사람은 아직 안내를 읽고 동의하지 않은 사람입니다.
 *
 * <p>화면은 이 목록으로 자기를 가립니다. 내 기록이 없으면 입장 안내를 보여 주고, 있으면 곧장
 * 세션으로 보냅니다. 통화 중에 새로고침해도 안내부터 다시 묻지 않는 근거가 이것입니다.
 *
 * <p><b>안내문 판은 싣지 않습니다.</b> 서버는 보관하지만(`R-CON-17`) 화면이 쓸 곳이 없습니다 —
 * 「문구가 바뀌면 이미 동의한 사람에게 다시 묻는다」는 규칙이 아직 없기 때문입니다. 그 규칙이
 * 생기면 그때 싣습니다.
 *
 * @param userId 참가자
 * @param consentedAt 동의한 시각
 * @param joinedAt 실제로 들어간 시각. 상대를 기다리는 중이면 {@code null}
 */
@Schema(description = "세션 참가자 한 사람의 기록")
public record SessionParticipationResponse(
        @Schema(description = "참가자 사용자 식별자.", example = "12") Integer userId,
        @Schema(description = "녹음·전사·요약에 동의한 시각.") OffsetDateTime consentedAt,
        @Schema(description = "실제로 들어간 시각. 상대 동의를 기다리는 중이면 null 이다.")
                OffsetDateTime joinedAt) {

    public static SessionParticipationResponse from(ParticipationRecord entity) {
        return new SessionParticipationResponse(
                entity.getUserId(), atUtc(entity.getConsentedAt()), atUtc(entity.getJoinedAt()));
    }

    /**
     * 저장된 값에는 offset 이 없고 서버는 UTC 로 씁니다. 계약은 경계에서 offset 을 요구하므로
     * (BACKEND_CONVENTIONS 11.1) 여기서 붙입니다.
     */
    private static OffsetDateTime atUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
