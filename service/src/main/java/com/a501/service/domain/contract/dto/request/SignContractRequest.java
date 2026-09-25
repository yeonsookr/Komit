package com.a501.service.domain.contract.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 계약서 서명. 서명판에 그린 그림을 먼저 올려 받은 식별자를 보냅니다. */
@Schema(description = "계약서 서명 요청.")
public record SignContractRequest(
        @Schema(description = "서명판에 그린 그림의 파일 식별자.", example = "41") @NotNull
                Integer signatureImageFileId) {}
