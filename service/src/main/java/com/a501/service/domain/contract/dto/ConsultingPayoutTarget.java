package com.a501.service.domain.contract.dto;

/**
 * 대금 지급을 아직 마치지 않은 체결된 컨설팅 계약 한 건.
 *
 * <p>정산 도메인이 계약 엔티티 없이 지급을 판단하도록 넘기는 결과 객체입니다. (BACKEND_CONVENTIONS 7.1)
 *
 * @param contractId 계약 id. Escrow 에는 이 값을 문자열로 걸어 두었습니다
 * @param consultingAgreementId 컨설팅 합의 id. 세션을 찾고 완료를 적을 때 씁니다
 * @param amount 계약 금액(원). 수수료를 떼기 전 총액입니다 (`R-PAY-07`)
 * @param clientId 의뢰인
 * @param developerId 개발자. 대금을 받는 사람입니다
 */
public record ConsultingPayoutTarget(
        Integer contractId,
        Integer consultingAgreementId,
        int amount,
        Integer clientId,
        Integer developerId) {}
