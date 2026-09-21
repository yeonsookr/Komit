package com.a501.service.domain.account.dto.response;

/**
 * 회원가입 때 발급한 데모 계좌. 은행 앱이 없어 번호를 확인할 길이 없으므로
 * 본인에게만 가리지 않은 번호를 준다. (R-ACC-14)
 */
public record MemberAccountResponse(String bankCode, String accountNumber) {}
