package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계약 취소를 DB 에 적는 짧은 트랜잭션. 환불처럼 바깥 서버를 부르는 일은 {@link ContractCancellationService} 가
 * 이 트랜잭션이 끝난 뒤에 한다. (BACKEND_CONVENTIONS 16장)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractCancellationStore {
    private final ContractRepository contractRepository;
    private final ConsultingAgreementRepository agreementRepository;
    private final RoomQueryService roomQueryService;

    /**
     * 계약을 취소로 적는다. 이미 취소됐으면 아무것도 바꾸지 않고 그대로 통과한다 — 환불을 이어가려고 다시 부르는 요청이다.
     *
     * <p>계약 행을 잠가 개발자의 서명과 줄 세운다. 서명이 먼저 끝나 성립했으면 취소하지 않는다. (`R-CON-21`)
     *
     * @return 의뢰인 id. 예치금을 돌려받을 사람이다
     * @throws BusinessException {@link ContractErrorCode#CONTRACT_NOT_FOUND} 없거나 당사자가 아닐 때,
     *                           {@link CommonErrorCode#FORBIDDEN} 개발자가 불렀을 때,
     *                           {@link ContractErrorCode#CONTRACT_CONCLUDED} 이미 성립했을 때
     */
    @Transactional
    public Integer markCancelled(Integer contractId, Integer userId) {
        Contract contract = contractRepository
                .findWithLockByIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        // 당사자가 아니면 계약이 있다는 사실도 알리지 않는다. (BACKEND_CONVENTIONS 12.1)
        RoomParties parties = roomQueryService
                .findParties(contract.getRoomId())
                .filter(found -> found.includes(userId))
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        // 취소는 돈을 낸 쪽이 한다. 개발자는 계약이 있다는 것을 알므로 감추지 않고 403 이다.
        if (!Objects.equals(parties.clientId(), userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (contract.isCancelled()) {
            return parties.clientId();
        }
        if (contract.isConcluded()) {
            throw new BusinessException(ContractErrorCode.CONTRACT_CONCLUDED);
        }

        contract.cancel();
        agreementRepository.findByContractIdAndDeletedFalse(contractId).ifPresent(ConsultingAgreement::terminate);
        return parties.clientId();
    }
}
