package com.a501.service.domain.file;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 파일 도메인 실패 코드. 상태·코드·문구는 목 명세의 해당 API 설명을 그대로 옮겼습니다. */
public enum FileErrorCode implements ErrorCode {
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE", "올릴 수 없는 형식입니다."),
    UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "업로드 건을 찾을 수 없습니다."),
    UPLOAD_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "UPLOAD_NOT_COMPLETED",
            "아직 파일이 올라오지 않았습니다. 올리기를 마친 뒤 다시 시도해 주세요."),
    UPLOAD_SIZE_MISMATCH(HttpStatus.BAD_REQUEST, "UPLOAD_SIZE_MISMATCH",
            "올린 파일이 알린 것과 다릅니다. 처음부터 다시 올려 주세요."),
    UPLOAD_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "UPLOAD_TYPE_MISMATCH",
            "올린 파일의 형식이 알린 것과 다릅니다. 처음부터 다시 올려 주세요."),
    UPLOAD_EXPIRED(HttpStatus.BAD_REQUEST, "UPLOAD_EXPIRED",
            "올릴 수 있는 시간이 지났습니다. 처음부터 다시 올려 주세요."),
    /**
     * 리소스에 붙일 수 없는 파일. 올리기 완료 검증을 통과하지 않았거나, 남이 올렸거나, 없는 파일입니다.
     * 셋을 가르지 않는 것은 파일의 존재를 드러내지 않기 위해서입니다.
     */
    FILE_NOT_ATTACHABLE(HttpStatus.BAD_REQUEST, "FILE_NOT_ATTACHABLE", "쓸 수 없는 파일입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    FileErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
