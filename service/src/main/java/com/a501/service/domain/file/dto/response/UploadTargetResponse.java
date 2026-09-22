package com.a501.service.domain.file.dto.response;

import java.time.OffsetDateTime;
import java.util.Map;

public record UploadTargetResponse(
        Integer uploadId,
        String uploadUrl,
        OffsetDateTime expiresAt,
        Map<String, String> headers) {
}
