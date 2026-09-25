package com.a501.service.domain.mypage;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 마이페이지 도메인 실패 코드. 상태·코드·문구는 목 명세의 해당 API 설명을 그대로 옮겼습니다. */
public enum MyPageErrorCode implements ErrorCode {
    /** 진행 중인 의뢰나 남은 예치금·소득이 있어 탈퇴를 막았다. (R-ACC-05) */
    WITHDRAWAL_BLOCKED(HttpStatus.CONFLICT, "WITHDRAWAL_BLOCKED", "진행 중인 의뢰나 남은 돈이 있어 탈퇴할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    MyPageErrorCode(HttpStatus status, String code, String message) {
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
