package com.a501.service.domain.consulting.dto.response;

import com.a501.service.domain.consulting.entity.Reservation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 세션 예약. 한 세션에 하나만 있습니다.
 *
 * @param reservationId 예약 id
 * @param scheduledAt 예약한 일시
 * @param status 예약 처리 상태
 */
@Schema(description = "세션 예약. 한 세션에 하나만 있다.")
public record ReservationResponse(
        @Schema(description = "예약 식별자.", example = "71") Integer reservationId,
        @Schema(description = "예약한 일시.", example = "2026-08-29T05:00:00Z") OffsetDateTime scheduledAt,
        @Schema(
                        description =
                                "예약 처리. 계약서를 주고받는 동안은 PROPOSED 이고, 양쪽이 서명하면 CONFIRMED 가 된다."
                                        + " 서명한 뒤에는 일정을 바꾸지 않는다. DISABLED 는 계약이 파기되었을 때만 쓴다.",
                        example = "CONFIRMED")
                String status) {

    /**
     * 엔티티를 응답으로 바꿉니다. 엔티티는 밖으로 나가지 않습니다.
     *
     * <p>저장된 값은 offset 이 없는 시각이고 서버는 UTC 로 씁니다. 계약은 경계에서 offset 을
     * 요구하므로(BACKEND_CONVENTIONS 11.1) 여기서 UTC 를 붙입니다.
     *
     * @param entity 조회한 엔티티
     * @return 응답 DTO
     */
    public static ReservationResponse from(Reservation entity) {
        return new ReservationResponse(
                entity.getId(),
                entity.getScheduledAt().atOffset(ZoneOffset.UTC),
                entity.getStatus());
    }
}
