package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.account.service.MemberAccountProvisioningService;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.client.EscrowDepositClient;
import com.a501.service.domain.contract.dto.request.DepositRequest;
import com.a501.service.domain.contract.dto.response.DepositResponse;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import com.a501.service.domain.user.service.UserQueryService;
import java.math.BigInteger;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계약 금액을 예치한다.
 *
 * <p><b>돈의 원장은 이 서버가 갖지 않는다.</b> 결제와 보관은 Escrow 가 맡고, 여기서는 누가 얼마를
 * 걸어야 하는지를 모아 넘기고 상태를 물어 옮길 뿐이다. (`R-PAY-05`)
 *
 * <p><b>예치는 의뢰인만 한다.</b> 돈을 내는 쪽이다. 방의 당사자는 계약이 있다는 것을 아므로 개발자에게는
 * 감추지 않고 403 으로 답한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractDepositService {
    private final ContractRepository contractRepository;
    private final ConsultingAgreementRepository consultingAgreementRepository;
    private final DevelopmentContractRepository developmentContractRepository;
    private final RoomQueryService roomQueryService;
    private final UserQueryService userQueryService;
    private final MemberAccountProvisioningService accountProvisioning;
    private final EscrowDepositClient escrowDepositClient;

    /**
     * 예치를 건다.
     *
     * <p>돈은 회원가입 때 발급한 의뢰인의 계좌에서 빠진다. 계좌를 고르지 않으므로 보낸
     * {@code paymentMethodId} 는 그 계좌를 가리키는 값이다. (`R-PAY-09`)
     *
     * @throws BusinessException {@link ContractErrorCode#CONTRACT_NOT_FOUND} 없거나 내 계약이 아닐 때,
     *                           {@link CommonErrorCode#FORBIDDEN} 의뢰인이 아닐 때,
     *                           {@link ContractErrorCode#CONTRACT_CANCELLED} 취소된 계약일 때,
     *                           {@link ContractErrorCode#CONTRACT_NOT_SIGNED} 의뢰인이 서명하기 전일 때,
     *                           {@link ContractErrorCode#ALREADY_DEPOSITED} 이미 걸었을 때
     */
    @Transactional
    public DepositResponse deposit(Integer contractId, Integer userId, DepositRequest request) {
        Contract contract = readableContract(contractId, userId, true);

        if (contract.isCancelled()) {
            throw new BusinessException(ContractErrorCode.CONTRACT_CANCELLED);
        }
        // R-CON-20: 의뢰인은 자기가 서명한 계약서에만 돈을 맡긴다. 서명하면 계약서를 고칠 수 없으므로(R-CON-02)
        // 예치 금액이 계약 금액과 어긋나지 않는다. 개발자 서명은 기다리지 않는다 — 의뢰인은 서명과 결제를 한 번에
        // 한다. 계약이 성립하기 전이므로 지급은 양쪽 서명 뒤에만 나간다(R-CON-01).
        if (!contract.isSignedBy(true)) {
            throw new BusinessException(ContractErrorCode.CONTRACT_NOT_SIGNED);
        }

        // 같은 계약에 두 번 걸지 않는다. 멱등키는 같은 요청의 재시도를 가릴 뿐이다.
        if (escrowDepositClient.findByContract(String.valueOf(contractId)).isPresent()) {
            throw new BusinessException(ContractErrorCode.ALREADY_DEPOSITED);
        }

        MemberAccountProvisioningService.MemberAccount account =
                accountProvisioning.provision(userId);
        String depositorName = userQueryService
                .findName(userId)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));

        EscrowDepositClient.Deposit deposit = escrowDepositClient.create(
                String.valueOf(contractId),
                contract.getContractType(),
                depositorName,
                amountOf(contract),
                account.userKey(),
                account.bankCode(),
                account.accountNo(),
                request.idempotencyKey());

        return toResponse(contractId, deposit);
    }

    /**
     * 예치 상태를 읽는다.
     *
     * @throws BusinessException {@link ContractErrorCode#CONTRACT_NOT_FOUND} 없거나 내 계약이 아니거나
     *                           아직 예치를 걸지 않았을 때
     */
    public DepositResponse getDeposit(Integer contractId, Integer userId) {
        readableContract(contractId, userId, false);
        EscrowDepositClient.Deposit deposit = escrowDepositClient
                .findByContract(String.valueOf(contractId))
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        return toResponse(contractId, deposit);
    }

    /**
     * 볼 수 있는 계약인지 가린다.
     *
     * @param clientOnly 참이면 의뢰인만 통과한다. 예치는 돈을 내는 쪽만 건다
     */
    private Contract readableContract(Integer contractId, Integer userId, boolean clientOnly) {
        Contract contract = contractRepository
                .findByIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        // 당사자가 아니면 계약이 있다는 사실도 알리지 않는다. (BACKEND_CONVENTIONS 12.1)
        RoomParties parties = roomQueryService
                .findParties(contract.getRoomId())
                .filter(found -> found.includes(userId))
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        if (clientOnly && !Objects.equals(parties.clientId(), userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return contract;
    }

    /** 걸 금액. 갈래에 따라 컨설팅 합의나 개발 계약의 합계를 읽는다. */
    private BigInteger amountOf(Contract contract) {
        Integer amount = Contract.ContractType.CONSULTING.name().equals(contract.getContractType())
                ? consultingAgreementRepository
                        .findByContractIdAndDeletedFalse(contract.getId())
                        .map(ConsultingAgreement::getTotalAmount)
                        .orElse(null)
                : developmentContractRepository
                        .findByContractIdAndDeletedFalse(contract.getId())
                        .map(DevelopmentContract::getTotalAmount)
                        .orElse(null);
        if (amount == null || amount <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        return BigInteger.valueOf(amount);
    }

    /** 시각은 Escrow 가 갖고 있고 목록 응답에는 없다. 아직 옮기지 않는다. */
    private DepositResponse toResponse(Integer contractId, EscrowDepositClient.Deposit deposit) {
        return new DepositResponse(
                contractId, deposit.depositedAmount(), deposit.status(), null);
    }
}
