package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.client.EscrowDepositClient;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개발자가 개발 계약을 착수한다.
 *
 * <p><b>양쪽이 서명하고 대금이 예치된 뒤에만 착수한다.</b> (R-CON-24) 예치는 Escrow 가 가지므로 착수할 때 물어본다. 착수하면
 * 개발 쪽 상태가 {@code IN_PROGRESS} 가 되고, 1차 작업이 시작되며 나머지 작업은 뒤로 잡힌다. 착수일은 계약서
 * 날짜 그대로다.
 *
 * <p>컨설팅 계약에는 착수가 없다. 세션 입장이 그 자리를 대신한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DevelopmentStartService {
    private final ContractRepository contractRepository;
    private final DevelopmentContractRepository developmentContractRepository;
    private final MilestoneRepository milestoneRepository;
    private final RoomQueryService roomQueryService;
    private final EscrowDepositClient escrowDepositClient;
    private final ContractDetailService contractDetailService;

    /**
     * 착수한다.
     *
     * @throws BusinessException {@link ContractErrorCode#CONTRACT_NOT_FOUND} 없는 계약이거나 당사자가 아니거나 개발
     *                           계약이 아닐 때,
     *                           {@link CommonErrorCode#FORBIDDEN} 의뢰인일 때,
     *                           {@link ContractErrorCode#CONTRACT_CANCELLED} 취소된 계약일 때,
     *                           {@link ContractErrorCode#CONTRACT_NOT_CONCLUDED} 양쪽이 서명하기 전일 때,
     *                           {@link ContractErrorCode#ALREADY_STARTED} 이미 착수했을 때,
     *                           {@link ContractErrorCode#DEPOSIT_REQUIRED} 예치가 끝나지 않았을 때,
     *                           {@link ContractErrorCode#ESCROW_UNAVAILABLE} 예치 여부를 확인하지 못했을 때
     */
    @Transactional
    public ContractResponse start(Integer contractId, Integer userId) {
        Contract contract = contractRepository.findWithLockByIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        RoomParties parties = roomQueryService.findParties(contract.getRoomId())
                .filter(found -> found.includes(userId))
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        if (!parties.developerUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        DevelopmentContract development = developmentContractRepository.findByContractIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        if (contract.isCancelled()) {
            throw new BusinessException(ContractErrorCode.CONTRACT_CANCELLED);
        }
        if (!contract.isConcluded()) {
            throw new BusinessException(ContractErrorCode.CONTRACT_NOT_CONCLUDED);
        }
        if (!development.isBeforeStart()) {
            throw new BusinessException(ContractErrorCode.ALREADY_STARTED);
        }
        if (!isDeposited(contractId)) {
            throw new BusinessException(ContractErrorCode.DEPOSIT_REQUIRED);
        }

        development.start();
        startFirstWork(milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(
                development.getId()));
        return contractDetailService.getContract(userId, contractId);
    }

    /** Escrow 는 예치가 끝나기 전에는 예치 금액을 0 으로 준다. 컨설팅 예약 확정과 같은 기준이다. */
    private boolean isDeposited(Integer contractId) {
        return escrowDepositClient.findByContract(String.valueOf(contractId))
                .map(EscrowDepositClient.Deposit::depositedAmount)
                .map(amount -> amount.signum() > 0)
                .orElse(false);
    }

    /** 1차 일반 작업을 시작하고 나머지(인계 작업 포함)는 뒤로 잡는다. 작업 순서는 계약서에 담은 순서다. */
    private static void startFirstWork(List<Milestone> milestones) {
        boolean started = false;
        for (Milestone milestone : milestones) {
            if (!started && !milestone.isHandover()) {
                milestone.startWork();
                started = true;
            } else {
                milestone.schedule();
            }
        }
    }
}
