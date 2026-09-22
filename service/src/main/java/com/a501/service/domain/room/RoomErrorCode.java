package com.a501.service.domain.room;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum RoomErrorCode implements ErrorCode {
    /** 없는 방이거나 내가 들어가 있지 않은 방이다. 남의 방도 존재를 감추려 둘을 같게 다룬다. */
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "방을 찾을 수 없습니다."),

    /**
     * 갈래를 정할 수 없는 방이다. 이미 정한 상담방과, 갈래를 정해 열린 컨설팅·개발방이 함께
     * 여기 걸린다. 둘 다 고를 것이 남아 있지 않다는 뜻이라 같게 다룬다.
     */
    ROOM_ALREADY_CONVERTED(HttpStatus.CONFLICT, "ROOM_ALREADY_CONVERTED", "이미 갈래가 정해진 상담입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    RoomErrorCode(HttpStatus status, String code, String message) {
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
