package com.a501.service.domain.contract.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 계약 편집 중인 작업의 현재 문구로 확인 문장 후보를 요청한다. */
@Schema(description = "계약 편집 중인 작업의 확인 문장 후보 생성 요청.")
public record ContractQaSuggestionRequest(
        @Schema(description = "기존 요구사항 식별자. 아직 저장하지 않은 새 작업이면 null이다.", example = "312")
        Integer issueId,
        @Schema(description = "현재 계약 편집기에 적힌 작업 이름.", example = "관리자 API에 인증 추가")
        @NotNull
        @Size(min = 1, message = "{komit.minLength}")
        @Size(max = 200)
        String title,
        @Schema(description = "현재 계약 편집기에 적힌 작업 설명. 적지 않았으면 null이다.")
        @Size(min = 1, message = "{komit.minLength}")
        @Size(max = 1000)
        String detail) {}
