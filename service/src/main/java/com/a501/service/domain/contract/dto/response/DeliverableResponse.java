package com.a501.service.domain.contract.dto.response;

import com.a501.service.domain.contract.entity.Deliverable;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 넘겨받을 인계물 하나.
 *
 * @param deliverableId 인계물 id
 * @param title 인계물 이름
 * @param fileId 넘겨받은 파일. 아직이면 {@code null}
 * @param deliveredAt 넘긴 시각. 아직이면 {@code null}
 */
@Schema(description = "넘겨받을 인계물 하나.")
public record DeliverableResponse(
        @Schema(description = "인계물 식별자.", example = "401") Integer deliverableId,
        @Schema(description = "인계물 이름.", example = "소스 파일 전체") String title,
        @Schema(description = "넘겨받은 파일 식별자. 아직이면 null 이다.", example = "88") Integer fileId,
        @Schema(description = "넘긴 시각. 아직이면 null 이다.") OffsetDateTime deliveredAt) {

    /**
     * 엔티티를 응답으로 바꿉니다.
     *
     * @param entity 인계물
     * @return 응답 DTO
     */
    public static DeliverableResponse from(Deliverable entity) {
        return new DeliverableResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getFileId(),
                atUtc(entity.getDeliveredAt()));
    }

    /** 저장값에는 offset 이 없고 서버는 UTC 로 씁니다. 경계에서 붙입니다. (BACKEND_CONVENTIONS 11.1) */
    private static OffsetDateTime atUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
