package com.a501.service.domain.consulting.client;

/**
 * AI 요약을 받지 못했다.
 *
 * <p>{@code BusinessException} 이 아니다. 기다리는 요청이 없어 사용자에게 나갈 응답이 없고,
 * 부르는 쪽은 요약을 비워 둔 채 끝낸다. 무엇이 잘못됐는지는 클라이언트가 이미 남겼다.
 */
public class SummaryFailedException extends RuntimeException {
    private final Reason reason;

    public SummaryFailedException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /** `job-contract.md` 6절의 세 갈래. */
    public enum Reason {
        /** GMS 에 닿지 못했다. 다시 부르면 될 수 있다 */
        UNAVAILABLE,
        /** 닿았으나 계약에 맞는 결과를 못 만들었다 */
        RESPONSE_INVALID,
        /** 이쪽이 계약과 다르게 보냈다. 코드 결함이다 */
        REQUEST_INVALID
    }
}
