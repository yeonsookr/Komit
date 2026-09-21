package com.a501.service.domain.file.dto.response;

import com.a501.service.common.time.SeoulTime;
import com.a501.service.domain.file.entity.StoredFile;

public record UploadedFileResponse(
        Integer fileId,
        String originalName,
        String contentType,
        Long sizeBytes,
        java.time.OffsetDateTime verifiedAt) {

    public static UploadedFileResponse from(StoredFile file) {
        return new UploadedFileResponse(
                Math.toIntExact(file.getFileId()),
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getVerifiedAt().atZone(SeoulTime.ZONE).toOffsetDateTime());
    }
}
