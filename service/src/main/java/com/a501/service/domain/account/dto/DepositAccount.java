package com.a501.service.domain.account.dto;

/**
 * 예치에서 돈이 빠지는 계좌를 화면에 보여 줄 값.
 *
 * @param provisionId 회원 발급 계좌 행의 식별자
 * @param maskedNumber 은행 이름과 계좌번호 끝 네 자리 (예: {@code 싸피은행 ···· 8842})
 */
public record DepositAccount(Long provisionId, String maskedNumber) {}
