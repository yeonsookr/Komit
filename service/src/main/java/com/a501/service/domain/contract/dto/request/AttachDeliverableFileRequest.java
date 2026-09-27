package com.a501.service.domain.contract.dto.request;

import com.a501.service.domain.contract.entity.DeliverableAccessStage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 인계물에 붙일 파일 하나. 목 명세 {@code AttachDeliverableRequest}와 같다.
 *
 * <p>파일은 업로드 3콜로 먼저 올리고 받은 {@code fileId}만 보낸다. 열람 단계는 계약서가 아니라 올리는 개발자가
 * 정한다. (R-QA-06)
 */
@Schema(description = "인계물에 붙일 파일.")
public record AttachDeliverableFileRequest(
        @Schema(description = "업로드 3콜을 마친 파일 식별자.", example = "71") @NotNull Long fileId,
        @Schema(description = "파일별 열람 단계. TEST는 인수 시험 중, PAID는 전액 지급 완료 뒤 열람한다.", example = "TEST")
                @NotNull
                DeliverableAccessStage accessStage) {}
