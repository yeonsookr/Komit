package com.a501.service.domain.contract.dto;

/**
 * 리뷰를 받을 계약. 리뷰 쪽이 거래를 마쳤는지 가릴 재료를 한 번에 담습니다.
 *
 * @param parties 계약과 두 당사자
 * @param contractType {@code CONSULTING} 또는 {@code DEVELOPMENT}
 * @param cancelled 의뢰인이 취소한 계약인지
 * @param consultingAgreementId 컨설팅 합의 id. 개발 계약이면 {@code null}
 * @param developmentCompleted 개발 계약이 끝났는지. 컨설팅이면 거짓
 */
public record ReviewableContract(
        ContractParties parties,
        String contractType,
        boolean cancelled,
        Integer consultingAgreementId,
        boolean developmentCompleted) {}
