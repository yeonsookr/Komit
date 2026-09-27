package com.a501.service.domain.contract.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 계약 체결 뒤 개발자가 더하는 산출물 항목. 목 명세 {@code AddDeliverableRequest}와 같다. */
@Schema(description = "계약 체결 후 추가하는 산출물 항목.")
public record AddDeliverableRequest(
        @Schema(description = "추가 산출물 제목.", example = "운영 설명서 보충")
                @NotNull
                @Size(min = 1, message = "{komit.minLength}")
                @Size(max = 100)
                String title) {}
