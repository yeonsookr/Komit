package com.a501.service.domain.file.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUploadRequest(
        @NotNull @Size(min = 1, message = "{komit.minLength}") @Size(max = 255) String originalName,
        @NotNull @Size(min = 1, message = "{komit.minLength}") @Size(max = 100) String contentType,
        @NotNull @Min(1) Long sizeBytes,
        @NotNull UploadPurpose purpose) {
}
