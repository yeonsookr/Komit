package com.a501.service.domain.developer;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 개발자 도메인 실패 코드. 상태·코드·문구는 목 명세({@code /api/users/me/developer-profile}, {@code /api/developers/{developerId}})의
 * 설명을 그대로 옮겼습니다.
 */
public enum DeveloperErrorCode implements ErrorCode {
    /**
     * 토큰의 주인에게 개발자 프로필이 없을 때. 대상을 요청에 담지 않아 남의 것을 가리킬 자리가 없으므로, 목 명세대로 404가 아니라 403입니다.
     */
    NO_DEVELOPER_PROFILE(HttpStatus.FORBIDDEN, "NO_DEVELOPER_PROFILE", "개발자 프로필이 없습니다."),
    /** 공개 프로필의 {@code developerId}가 없거나 탈퇴한 개발자일 때. 둘을 구분해 알려주지 않습니다. */
    DEVELOPER_NOT_FOUND(HttpStatus.NOT_FOUND, "DEVELOPER_NOT_FOUND", "개발자를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    DeveloperErrorCode(HttpStatus status, String code, String message) {
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
