package com.a501.service.domain.contract.dto.response;

import com.a501.service.domain.contract.entity.DeliverableMaterial;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "산출물에 등록한 인계 자료 하나. 파일이거나 URL이다.")
public record DeliverableMaterialResponse(
        @Schema(description = "인계 자료 식별자.", example = "501") Integer deliverableMaterialId,
        @Schema(description = "파일 자료면 업로드 완료된 파일 식별자, URL 자료면 null 이다.", example = "71") Long fileId,
        @Schema(description = "URL 자료면 인수 시험 중 방문할 주소, 파일 자료면 null 이다.") String url,
        @Schema(description = "열람 단계. TEST는 인수 시험 중, PAID는 전액 지급 완료 뒤 열람한다. URL 자료는 언제나 TEST다.",
                example = "TEST")
                String accessStage,
        @Schema(description = "이 자료를 등록한 시각.") OffsetDateTime deliveredAt) {

    public static DeliverableMaterialResponse from(DeliverableMaterial entity) {
        LocalDateTime createdAt = entity.getCreatedAt();
        return new DeliverableMaterialResponse(
                entity.getId(), entity.getFileId(), entity.getUrl(), entity.getAccessStage().name(),
                createdAt == null ? null : createdAt.atOffset(ZoneOffset.UTC));
    }
}
