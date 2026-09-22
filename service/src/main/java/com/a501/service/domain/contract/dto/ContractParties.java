package com.a501.service.domain.contract.dto;

/**
 * 계약 하나와 그 계약을 맺은 두 사람.
 *
 * <p>도메인 사이에 오가는 결과 객체입니다. 계약 식별자를 함께 담는 것은, 묻는 쪽이 대개
 * 「이 사람이 당사자인가」와 「어느 계약으로 돌아가나」를 한 번에 알아야 하기 때문입니다.
 *
 * @param contractId 계약 id
 * @param clientId 의뢰인
 * @param developerId 개발자
 */
public record ContractParties(Integer contractId, Integer clientId, Integer developerId) {

    /** 이 사람이 이 계약의 당사자인지. */
    public boolean includes(Integer userId) {
        return clientId.equals(userId) || developerId.equals(userId);
    }
}
