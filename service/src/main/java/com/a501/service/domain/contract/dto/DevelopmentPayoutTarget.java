package com.a501.service.domain.contract.dto;

/**
 * 대금을 지급할 개발 계약. 최종 인수 시험을 통과했고 아직 지급을 마치지 않았다.
 *
 * @param contractId 계약 id. 정산 식별자와 멱등키를 이 값으로 정한다
 * @param developmentContractId 개발 계약 id. 지급을 마친 뒤 완료를 적는다
 * @param amount 계약 금액. 작업 가격의 합이며 수수료를 떼기 전이다 (R-CON-04, R-PAY-07)
 * @param clientId 의뢰인
 * @param developerId 대금을 받을 개발자
 */
public record DevelopmentPayoutTarget(
        Integer contractId, Integer developmentContractId, int amount, Integer clientId, Integer developerId) {}
