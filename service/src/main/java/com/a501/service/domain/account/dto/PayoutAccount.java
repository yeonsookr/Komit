package com.a501.service.domain.account.dto;

/**
 * 대금을 받을 계좌. Escrow 지급 요청에 그대로 싣는 값입니다. (`R-PAY-14`)
 *
 * <p><b>원문 계좌번호를 담습니다.</b> 응답이나 로그에 내보내지 않습니다. {@link #toString()} 도
 * 번호를 가립니다. (`R-LEG-02`)
 *
 * @param bankCode 금융망 은행코드 (예: {@code 001})
 * @param accountNo 16자리 원문 계좌번호
 * @param holderName 금융망에 등록된 예금주명. Escrow 가 이 값을 금융망 예금주와 대조합니다
 */
public record PayoutAccount(String bankCode, String accountNo, String holderName) {
    @Override
    public String toString() {
        return "PayoutAccount[bankCode=" + bankCode + ", accountNo=****, holderName=" + holderName + "]";
    }
}
