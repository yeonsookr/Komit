package com.a501.service.domain.recommendation;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum RecommendationErrorCode implements ErrorCode {
    /** 없는 프로젝트이거나 내 프로젝트가 아니다. 존재를 알리지 않으려 둘을 같게 다룬다. */
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다."),
    /** 없는 추천 회차이거나 남의 회차다. */
    BATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "추천을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    RecommendationErrorCode(HttpStatus status, String code, String message) {
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
