package com.a501.service.domain.contract.service;

import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지급이 끝난 계약을 계약 쪽에 적는 창구. 정산 도메인이 합의 엔티티를 직접 만지지 않도록 둡니다.
 * (BACKEND_CONVENTIONS 7.1)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractSettlementService {
    private final ConsultingAgreementRepository agreementRepository;
    private final DevelopmentContractRepository developmentContractRepository;

    /**
     * 컨설팅 합의를 지급 완료로 적습니다. 없거나 지워진 합의면 아무 일도 하지 않습니다.
     *
     * <p>Escrow 가 지급을 확정한 뒤에만 부릅니다. 이 표시가 남으면 다음 회차부터 지급 대상으로 고르지 않습니다.
     *
     * @param consultingAgreementId 컨설팅 합의 id
     */
    @Transactional
    public void completeConsultingPayout(Integer consultingAgreementId) {
        agreementRepository.findByIdAndDeletedFalse(consultingAgreementId)
                .ifPresent(ConsultingAgreement::complete);
    }

    /**
     * 개발 계약을 지급 완료로 적습니다. 없거나 지워졌거나 이미 진행 중이 아니면 아무 일도 하지 않습니다.
     *
     * <p>Escrow 가 지급액과 수수료 이체를 모두 확정한 뒤에만 부릅니다. 이때부터 리뷰를 남길 수 있고, 의뢰인이 지급 뒤 해금
     * 파일을 받습니다. 다음 회차부터 지급 대상으로 고르지 않습니다. (`R-PAY-07`, `R-REV-02`, `R-QA-06`)
     *
     * @param developmentContractId 개발 계약 id
     */
    @Transactional
    public void completeDevelopmentPayout(Integer developmentContractId) {
        developmentContractRepository.findByIdAndDeletedFalse(developmentContractId)
                .filter(DevelopmentContract::isInProgress)
                .ifPresent(DevelopmentContract::complete);
    }
}
