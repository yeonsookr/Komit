package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.settlement.service.EscrowRefundService;
import com.a501.service.domain.settlement.service.EscrowRefundService.RefundOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 개발자가 서명하기 전에 의뢰인이 계약을 취소한다. 예치금이 있으면 전액 돌려준다. (`R-CON-21`, `R-PAY-04`)
 *
 * <p><b>취소를 먼저 적고 돈은 나중에 돌려준다.</b> 순서를 바꾸면 환불을 부르는 사이 개발자가 서명해 성립한 계약의 돈이
 * 빠져나갈 수 있다. 취소가 적힌 뒤에는 서명·예치가 막히므로 환불이 늦어져도 안전하다.
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> 취소 기록은 {@link ContractCancellationStore} 가 짧게 끝내고, 환불은 그 뒤에 부른다.
 */
@Service
@RequiredArgsConstructor
public class ContractCancellationService {
    private final ContractCancellationStore store;
    private final EscrowRefundService refundService;
    private final ContractDetailService contractDetailService;

    /**
     * 계약을 취소하고 예치금을 돌려준다. 같은 요청을 다시 보내도 된다 — 이미 취소된 계약이면 환불만 잇는다.
     *
     * @return 취소된 계약
     * @throws BusinessException {@link ContractErrorCode#CONTRACT_NOT_FOUND} 없거나 당사자가 아닐 때,
     *                           {@link CommonErrorCode#FORBIDDEN} 의뢰인이 아닐 때,
     *                           {@link ContractErrorCode#CONTRACT_CONCLUDED} 이미 성립했을 때,
     *                           {@link ContractErrorCode#REFUND_PENDING} 취소는 됐고 환불이 끝나지 않았을 때
     */
    public ContractResponse cancel(Integer contractId, Integer userId) {
        Integer clientId = store.markCancelled(contractId, userId);

        RefundOutcome refund = refundService.refundAll(contractId, clientId);
        if (refund == RefundOutcome.PENDING || refund == RefundOutcome.BLOCKED) {
            throw new BusinessException(ContractErrorCode.REFUND_PENDING);
        }
        return contractDetailService.getContract(userId, contractId);
    }
}
