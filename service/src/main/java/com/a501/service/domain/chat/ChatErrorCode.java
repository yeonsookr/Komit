package com.a501.service.domain.chat;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ChatErrorCode implements ErrorCode {
    /**
     * 없는 대화이거나 내가 들어가 있지 않은 대화다.
     *
     * <p>존재를 알리지 않으려 둘을 같게 다룬다. (BACKEND_CONVENTIONS 12.1) 이름표 모양이 틀린 것도
     * 여기로 묶어, 어떤 이름표가 있는지 더듬어 볼 수 없게 한다.
     */
    CHAT_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_NOT_FOUND", "대화를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ChatErrorCode(HttpStatus status, String code, String message) {
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
