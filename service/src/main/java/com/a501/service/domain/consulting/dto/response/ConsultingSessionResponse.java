package com.a501.service.domain.consulting.dto.response;

import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.entity.Reservation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 컨설팅 세션 하나.
 *
 * @param sessionId 컨설팅 세션 id
 * @param contractId 이 세션이 달린 계약 id. 세션 화면이 끝낸 뒤 돌아갈 방을 여기서 찾습니다
 * @param price 세션 금액
 * @param durationMinutes 계약서에 적힌 세션 시간(분)
 * @param reservation 이 세션의 예약
 * @param startedAt 양쪽이 모여 처음 들어간 시각. 아직이면 {@code null}
 * @param endedAt 세션을 마친 시각. 아직이면 {@code null}
 * @param chatRefId 세션 대화 참조 키
 */
@Schema(description = "컨설팅 세션 하나. 공통 래퍼 없이 리소스를 그대로 반환한다.")
public record ConsultingSessionResponse(
        @Schema(description = "컨설팅 세션 식별자.", example = "55") Integer sessionId,
        @Schema(
                        description =
                                "이 세션이 달린 계약 식별자. 세션 화면은 방 밖에 있는 별도 화면이라 세션 식별자만으로"
                                        + " 열리고, 끝낸 뒤 어느 방으로 돌아갈지가 이 값에서 나온다.",
                        example = "120")
                Integer contractId,
        @Schema(description = "세션 금액. 원 단위 정수다.", example = "50000") Long price,
        @Schema(description = "계약서에 적힌 세션 시간(분).", example = "30") Integer durationMinutes,
        @Schema(description = "이 세션의 예약. 한 세션에 하나뿐이며 이 시각에 진행한다.")
                ReservationResponse reservation,
        @Schema(description = "양쪽이 모여 처음 들어간 시각. 아직 시작하지 않았으면 null 이다.")
                OffsetDateTime startedAt,
        @Schema(description = "세션을 마친 시각. 아직 끝나지 않았으면 null 이다.") OffsetDateTime endedAt,
        @Schema(description = "세션 대화 참조 키. 방 대화와 다르다.", example = "session-55-chat")
                String chatRefId) {

    /**
     * 엔티티를 응답으로 바꿉니다. 엔티티는 밖으로 나가지 않습니다.
     *
     * <p>계약 식별자는 세션이 들고 있지 않습니다. 세션은 컨설팅 합의에 달려 있고 그 위가 계약이라,
     * 계약 도메인에 물어 받은 값을 넣습니다.
     *
     * @param entity 조회한 세션
     * @param contractId 계약 도메인에서 받은 계약 id
     * @param reservation 이 세션의 예약
     * @return 응답 DTO
     */
    public static ConsultingSessionResponse of(
            ConsultingSession entity, Integer contractId, Reservation reservation) {
        return new ConsultingSessionResponse(
                entity.getId(),
                contractId,
                entity.getPrice().longValue(),
                entity.getDurationMinutes(),
                ReservationResponse.from(reservation),
                atUtc(entity.getStartedAt()),
                atUtc(entity.getEndedAt()),
                entity.getChatRefId());
    }

    /** 저장된 값에는 offset 이 없고 서버는 UTC 로 씁니다. 계약은 경계에서 offset 을 요구합니다. */
    private static OffsetDateTime atUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
