package com.a501.service.domain.contract.controller;

import com.a501.service.domain.file.service.FileLookupService.FileContent;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 인계 파일을 내려받는 응답. 목과 같이 형식은 언제나 {@code application/octet-stream} 이고 이름은
 * {@code Content-Disposition} 에 담는다.
 *
 * <p>올린 사람이 정한 형식을 그대로 돌려주지 않는다. 인계 파일은 형식 제한 없이 받으므로 브라우저가 열어 보이지 않고
 * 저장만 하게 둔다. 한글 이름은 RFC 6266 의 {@code filename*} 로 함께 싣는다.
 */
final class FileDownloads {
    /** 이름을 모를 때. 목의 파일 이름과 다르지만 FE 가 이름을 못 읽었을 때 쓰는 값과 같다. */
    static final String FALLBACK_NAME = "deliverable";

    private FileDownloads() {
    }

    static ResponseEntity<InputStreamResource> attachment(FileContent file) {
        String name = file.originalName() == null || file.originalName().isBlank() ? FALLBACK_NAME : file.originalName();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString());
        if (file.sizeBytes() != null) {
            response.contentLength(file.sizeBytes());
        }
        return response.body(new InputStreamResource(file.body()));
    }
}
