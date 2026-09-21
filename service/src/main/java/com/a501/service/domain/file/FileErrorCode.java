package com.a501.service.domain.file;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 파일 도메인 실패 코드. 상태·코드·문구는 목 명세의 해당 API 설명을 그대로 옮겼습니다. */
public enum FileErrorCode implements ErrorCode {
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
