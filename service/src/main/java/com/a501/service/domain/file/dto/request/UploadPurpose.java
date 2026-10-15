package com.a501.service.domain.file.dto.request;

import java.util.Locale;
import java.util.Set;

/** R-FILE-02: 파일 용도별 허용 형식. 파라미터가 붙은 미디어 형식은 기본 MIME으로 판정합니다. */
public enum UploadPurpose {
    PROFILE_IMAGE(Set.of("image/png", "image/jpeg", "image/webp")),
    CAREER_EVIDENCE(Set.of("application/pdf", "image/png", "image/jpeg")),
    PORTFOLIO_IMAGE(Set.of("image/png", "image/jpeg", "image/webp")),
    SIGNATURE(Set.of("image/png")),
    DELIVERABLE(Set.of()),
    SESSION_RECORDING(Set.of("audio/webm", "audio/mp4")),
    REPORT_EVIDENCE(Set.of("application/pdf", "image/png", "image/jpeg", "image/webp"));

    private final Set<String> allowedContentTypes;

    UploadPurpose(Set<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }

    public boolean accepts(String contentType) {
        if (contentType == null) {
            return false;
        }
        String baseType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return !baseType.isEmpty() && (this == DELIVERABLE || allowedContentTypes.contains(baseType));
    }

    public String keySegment() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
