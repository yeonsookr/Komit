package com.a501.service.domain.settlement;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Escrow 지급 호출의 실패 갈래. 지금은 사용자 API 없이 서버가 스스로 지급하므로 응답으로 나가지 않고,
 * 다음 회차에 무엇을 할지를 가르는 데만 씁니다.
 */
public enum SettlementErrorCode implements ErrorCode {
    /** 결과를 알 수 없다. 돈이 나갔을 수도 있으므로 같은 멱등키로만 다시 부릅니다. */
    ESCROW_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ESCROW_UNAVAILABLE", "지금은 대금을 지급할 수 없습니다. 잠시 뒤 다시 시도해 주세요."),
    /** 신고로 자금이 동결되어 있다. 해제될 때까지 기다립니다. (`R-RPT-05`) */
    ESCROW_HELD(HttpStatus.CONFLICT, "ESCROW_HELD", "자금이 동결되어 대금을 지급할 수 없습니다."),
    /** Escrow 가 요청을 거절했다. 자동으로 다시 부르지 않고 사람이 확인합니다. */
    PAYOUT_REJECTED(HttpStatus.CONFLICT, "PAYOUT_REJECTED", "대금 지급이 거절되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    SettlementErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
