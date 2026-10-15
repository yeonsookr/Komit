package com.a501.service.domain.consulting;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ConsultingErrorCode implements ErrorCode {
    /** 없는 세션이거나 그 세션의 당사자가 아니다. 존재를 알리지 않으려 둘을 같게 다룬다. */
    SESSION_NOT_FOUND(
            HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "세션을 찾을 수 없습니다."),
    SESSION_ENDED(
            HttpStatus.CONFLICT, "SESSION_ENDED", "이미 끝난 세션입니다."),
    CONSENT_REQUIRED(
            HttpStatus.CONFLICT,
            "CONSENT_REQUIRED",
            "상대가 아직 들어오지 않았습니다. 양쪽이 모두 동의해야 시작할 수 있습니다."),
    ENTRY_NOT_OPEN(
            HttpStatus.CONFLICT, "ENTRY_NOT_OPEN", "아직 입장할 수 있는 시각이 아닙니다."),
    /**
     * 입장 API 는 「아직 이른 시각」과 「이미 끝난 세션」을 한 코드로 답한다. 티켓 발급이 둘을 갈라
     * 쓰는 것과 다른데, 목 명세가 그렇게 정해 두었다.
     */
    SESSION_NOT_OPEN(
            HttpStatus.CONFLICT,
            "SESSION_NOT_OPEN",
            "아직 입장할 수 없습니다. 시작 10분 전부터 들어갈 수 있습니다."),
    /** 의뢰인이 취소한 계약의 세션이다. 동의도 기록하지 않고 통화 채널도 열지 않는다. (`R-CON-21`) */
    SESSION_CANCELLED(
            HttpStatus.CONFLICT, "SESSION_CANCELLED", "취소된 계약이라 세션을 열 수 없습니다."),
    /** 녹음은 세션을 마친 뒤에만 받는다. 끝나기 전에는 몇 분짜리인지도 정해지지 않았다. */
    SESSION_NOT_ENDED(
            HttpStatus.BAD_REQUEST, "SESSION_NOT_ENDED", "아직 끝나지 않은 세션입니다."),
    /** 참가자당 한 건이다. 같은 파일을 다시 보내는 것은 성공으로 다루고 여기 오지 않는다. */
    RECORDING_ALREADY_UPLOADED(
            HttpStatus.CONFLICT, "RECORDING_ALREADY_UPLOADED", "이미 녹음을 올렸습니다."),
    /** 세션은 끝났지만 전사나 요약이 아직 끝나지 않았다. 화면은 이것을 「만드는 중」으로 읽는다. */
    SUMMARY_NOT_READY(
            HttpStatus.CONFLICT, "SUMMARY_NOT_READY", "요약을 아직 만드는 중입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ConsultingErrorCode(HttpStatus status, String code, String message) {
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
