package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.consulting.entity.Reservation;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ReservationRepository;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.client.EscrowDepositClient;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 컨설팅 세션 예약을 확정한다.
 *
 * <p><b>양측 서명과 대금 예치가 모두 끝나야 확정한다.</b> 서명이 먼저 끝날 수도, 예치가 먼저 끝날 수도
 * 있으므로 서명할 때와 예치할 때 둘 다 여기를 부른다. 확정 전에는 세션에 들어가지 못한다. (`R-CON-22`)
 *
 * <p>계약 하나에 세션은 하나다. 예약이 없는 옛 계약은 넘어간다.
 */
@Component
@RequiredArgsConstructor
class ConsultingReservationConfirmation {
    private final ConsultingAgreementRepository consultingAgreementRepository;
    private final ConsultingSessionRepository consultingSessionRepository;
    private final ReservationRepository reservationRepository;
    private final EscrowDepositClient escrowDepositClient;

    /**
     * 서명한 뒤. 계약이 성립했으면 대금이 예치됐는지 Escrow 에 물어 확정한다.
     *
     * @throws BusinessException {@link ContractErrorCode#ESCROW_UNAVAILABLE} 예치 여부를 확인하지 못했을 때.
     *                           확정을 빠뜨린 채 서명만 남지 않도록 서명도 되돌린다.
     */
    void afterSigned(Contract contract) {
        if (!awaitsConfirmation(contract)) {
            return;
        }
        boolean deposited = escrowDepositClient
                .findByContract(String.valueOf(contract.getId()))
                .map(ConsultingReservationConfirmation::isDeposited)
                .orElse(false);
        if (deposited) {
            confirm(contract);
        }
    }

    /** 예치한 뒤. 예치가 끝났고 계약도 성립했으면 확정한다. */
    void afterDeposited(Contract contract, EscrowDepositClient.Deposit deposit) {
        if (awaitsConfirmation(contract) && isDeposited(deposit)) {
            confirm(contract);
        }
    }

    private static boolean awaitsConfirmation(Contract contract) {
        return Contract.ContractType.CONSULTING.name().equals(contract.getContractType())
                && contract.isConcluded();
    }

    /** Escrow 는 예치가 끝나기 전에는 예치 금액을 0 으로 준다. */
    private static boolean isDeposited(EscrowDepositClient.Deposit deposit) {
        return deposit.depositedAmount() != null && deposit.depositedAmount().signum() > 0;
    }

    private void confirm(Contract contract) {
        consultingAgreementRepository
                .findByContractIdAndDeletedFalse(contract.getId())
                .flatMap(agreement -> consultingSessionRepository
                        .findAllByConsultingAgreementIdAndDeletedFalseOrderByIdAsc(agreement.getId())
                        .stream()
                        .findFirst())
                .flatMap(session -> reservationRepository.findByConsultingSessionIdAndDeletedFalse(session.getId()))
                .ifPresent(Reservation::confirm);
    }
}
