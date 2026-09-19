package com.a501.service.domain.defaults;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 기준 API의 도메인 실패 코드. 상태·코드·문구는 목 명세({@code GET /api/default/{id}})의 설명을 글자 그대로 옮겼습니다.
 *
 * <p>목이 함께 문서화한 {@code FORBIDDEN}·{@code INTERNAL_ERROR}·{@code INVALID_REQUEST}는 공통 코드이므로
 * {@link com.a501.service.common.exception.CommonErrorCode}에 있고 여기 다시 두지 않습니다.
 */
public enum DefaultErrorCode implements ErrorCode {
    /**
     * 요청한 id의 행이 없거나 소프트 삭제된 경우. 목 명세대로 타인의 리소스도 존재를 드러내지 않기 위해 같은 응답을 씁니다.
     * (BACKEND_CONVENTIONS 12.1)
     */
    DEFAULT_NOT_FOUND(HttpStatus.NOT_FOUND, "DEFAULT_NOT_FOUND", "요청한 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    DefaultErrorCode(HttpStatus status, String code, String message) {
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
