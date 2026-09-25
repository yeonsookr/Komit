package com.a501.service.domain.settlement.service;

/** 컨설팅 지급 한 회차에서 계약 한 건에 일어난 일. 로그와 테스트가 읽습니다. */
public enum PayoutOutcome {
    /** 세션이 없거나 예약 시각을 모르는 세션이 있다. */
    NO_SCHEDULE(false),
    /** 이의 제기 기간이 아직 지나지 않았다. (`R-CON-10`) */
    NOT_DUE(false),
    /** 열리지 않았거나 종료가 확정되지 않은 세션이 있다. 자동 지급하지 않는다. (`R-CON-12`) */
    SESSION_EXCEPTION(true),
    /** 해결되지 않은 신고가 있다. (`R-CON-11`) */
    REPORTED(true),
    /** 계약 금액이 없다. */
    NO_AMOUNT(true),
    /** 개발자가 받을 계좌를 등록하지 않았다. (`R-PAY-14`) */
    NO_ACCOUNT(true),
    /** 다른 서버가 이 계약을 처리하고 있다. */
    BUSY(false),
    /** Escrow 에 예치가 없다. */
    NOT_DEPOSITED(true),
    /** 예치가 완료 상태가 아니거나 이미 닫혔다. */
    NOT_PAYABLE(true),
    /** 예치된 금액이 계약 금액과 다르다. 자동으로 지급하지 않고 사람이 확인한다. */
    AMOUNT_MISMATCH(true),
    /** 신고로 자금이 동결되어 있다. (`R-RPT-05`) */
    HELD(true),
    /** Escrow 가 이 지급을 처리하고 있다. 끝나기를 기다린다. */
    IN_PROGRESS(true),
    /** 지급을 부탁했고 아직 확정되지 않았다. 다음 회차에 같은 멱등키로 잇는다. */
    SENT(true),
    /** 결과를 알 수 없다. 다음 회차에 같은 멱등키로 잇는다. (`R-PAY-12`) */
    UNKNOWN(true),
    /** Escrow 가 요청을 거절했다. 사람이 확인한다. */
    REJECTED(true),
    /** 이 지급의 거래가 실패로 확정됐다. 자동으로 다시 부르지 않고 사람이 확인한다. */
    STOPPED(true),
    /** 지급이 확정되어 계약에 완료를 적었다. */
    PAID(true);

    private final boolean noteworthy;

    PayoutOutcome(boolean noteworthy) {
        this.noteworthy = noteworthy;
    }

    /** 로그로 남길 만한 일인지. 기간이 안 됐거나 남이 처리 중인 것은 매 회차 반복되므로 남기지 않습니다. */
    public boolean isNoteworthy() {
        return noteworthy;
    }
}
