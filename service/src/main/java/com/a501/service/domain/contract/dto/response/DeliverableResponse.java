package com.a501.service.domain.contract.dto.response;

import com.a501.service.domain.contract.entity.Deliverable;
import com.a501.service.domain.contract.entity.DeliverableMaterial;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 넘겨받을 인계물 하나.
 *
 * @param deliverableId 인계물 id
 * @param title 인계물 이름
 * @param fileId 넘겨받은 파일. 아직이면 {@code null}
 * @param deliveredAt 넘긴 시각. 가장 최근에 인계 자료를 등록한 시각이고, 아직이면 {@code null}
 */
@Schema(description = "넘겨받을 인계물 하나.")
public record DeliverableResponse(
        @Schema(description = "인계물 식별자.", example = "401") Integer deliverableId,
        @Schema(description = "인계물 이름.", example = "소스 파일 전체") String title,
        @Schema(description = "넘겨받은 파일 식별자. 아직이면 null 이다.", example = "88") Integer fileId,
        @Schema(description = "넘긴 시각. 가장 최근에 인계 자료를 등록한 시각이고, 아직이면 null 이다.") OffsetDateTime deliveredAt,
        @Schema(description = "서명된 계약서에 포함된 산출물 항목이면 true, 체결 뒤 개발자가 추가한 항목이면 false다.")
                boolean isContractual,
        @Schema(description = "이 산출물에 등록한 인계 자료. URL·TEST 파일·PAID 파일을 함께, 각각 여러 개 등록할 수 있다.")
                List<DeliverableMaterialResponse> materials) {

    /**
     * 엔티티를 응답으로 바꿉니다.
     *
     * @param entity 인계물
     * @return 응답 DTO
     */
    public static DeliverableResponse from(Deliverable entity) {
        return from(entity, List.of());
    }

    public static DeliverableResponse from(Deliverable entity, List<DeliverableMaterial> materials) {
        return new DeliverableResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getFileId(),
                atUtc(entity.getDeliveredAt()),
                entity.isContractual(),
                materials.stream().map(DeliverableMaterialResponse::from).toList());
    }

    /** 저장값에는 offset 이 없고 서버는 UTC 로 씁니다. 경계에서 붙입니다. (BACKEND_CONVENTIONS 11.1) */
    private static OffsetDateTime atUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
