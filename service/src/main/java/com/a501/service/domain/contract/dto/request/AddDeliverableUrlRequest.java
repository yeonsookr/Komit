package com.a501.service.domain.contract.dto.request;

import com.a501.service.domain.contract.dto.request.validation.HttpUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 인수 시험용 URL 하나. 목 명세 {@code AttachDeliverableUrlRequest}와 같다. */
@Schema(description = "인수 시험용 URL 하나. 등록해도 같은 산출물의 다른 URL·파일은 그대로 남는다.")
public record AddDeliverableUrlRequest(
        @Schema(
                        description = "인수 시험 중 방문할 주소. 호스트가 있는 http·https 주소만 받는다. (R-QA-06)",
                        example = "https://demo.komit.example.com")
                @NotNull
                @Size(min = 1, message = "{komit.minLength}")
                @HttpUrl
                String url) {}
