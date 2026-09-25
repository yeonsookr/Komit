package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import com.a501.service.domain.settlement.service.EscrowRefundService;
import com.a501.service.domain.settlement.service.EscrowRefundService.RefundOutcome;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 개발자 서명 전 의뢰인의 계약 취소 (`R-CON-21`). */
class ContractCancellationTest {
    private static final Integer CONTRACT_ID = 120;
    private static final Integer ROOM_ID = 92;
    private static final Integer CLIENT_ID = 7;
    private static final Integer DEVELOPER_ID = 9;
    private static final Integer STRANGER_ID = 99;

    private final ContractRepository contracts = mock(ContractRepository.class);
    private final ConsultingAgreementRepository agreements = mock(ConsultingAgreementRepository.class);
    private final RoomQueryService rooms = mock(RoomQueryService.class);
    private final EscrowRefundService refunds = mock(EscrowRefundService.class);
    private final ContractDetailService details = mock(ContractDetailService.class);

    private final ContractCancellationStore store = new ContractCancellationStore(contracts, agreements, rooms);
    private final ContractCancellationService service = new ContractCancellationService(store, refunds, details);

    private Contract contract;
    private ConsultingAgreement agreement;

    @BeforeEach
    void setUp() {
        contract = Contract.of(ROOM_ID, Contract.ContractType.CONSULTING);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        contract.sign(true, 1, null);
        agreement = ConsultingAgreement.of(CONTRACT_ID, "결제 오류 상담", 50_000);

        when(contracts.findWithLockByIdAndDeletedFalse(CONTRACT_ID)).thenReturn(Optional.of(contract));
        when(agreements.findByContractIdAndDeletedFalse(CONTRACT_ID)).thenReturn(Optional.of(agreement));
        when(rooms.findParties(ROOM_ID)).thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_ID)));
        when(refunds.refundAll(CONTRACT_ID, CLIENT_ID)).thenReturn(RefundOutcome.REFUNDED);
    }

    @Test
    @DisplayName("의뢰인이 취소하면 계약은 취소, 합의는 파기로 적고 예치금을 돌려준다")
    void cancelsAndRefunds() {
        ContractResponse cancelled = mock(ContractResponse.class);
        when(details.getContract(CLIENT_ID, CONTRACT_ID)).thenReturn(cancelled);

        assertThat(service.cancel(CONTRACT_ID, CLIENT_ID)).isSameAs(cancelled);
        assertThat(contract.isCancelled()).isTrue();
        assertThat(agreement.getStatus()).isEqualTo(ConsultingAgreement.TERMINATED);
        verify(refunds).refundAll(CONTRACT_ID, CLIENT_ID);
    }

    @Test
    @DisplayName("예치 전이라 돌려줄 돈이 없어도 취소한다. 의뢰인이 서명하기 전에도 된다")
    void cancelsWithoutDeposit() {
        Contract unsigned = Contract.of(ROOM_ID, Contract.ContractType.CONSULTING);
        when(contracts.findWithLockByIdAndDeletedFalse(CONTRACT_ID)).thenReturn(Optional.of(unsigned));
        when(refunds.refundAll(CONTRACT_ID, CLIENT_ID)).thenReturn(RefundOutcome.NOTHING_TO_REFUND);

        service.cancel(CONTRACT_ID, CLIENT_ID);

        assertThat(unsigned.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("양쪽이 서명해 성립한 계약은 취소하지 않고 돈도 건드리지 않는다")
    void refusesConcludedContract() {
        contract.sign(false, 2, "계약 문구");

        assertThatThrownBy(() -> service.cancel(CONTRACT_ID, CLIENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_CONCLUDED);
        assertThat(contract.isCancelled()).isFalse();
        verify(refunds, never()).refundAll(CONTRACT_ID, CLIENT_ID);
    }

    @Test
    @DisplayName("개발자는 취소하지 못한다. 계약이 있다는 것은 알므로 403 이다")
    void developerCannotCancel() {
        assertThatThrownBy(() -> service.cancel(CONTRACT_ID, DEVELOPER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.FORBIDDEN);
        assertThat(contract.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("당사자가 아니면 계약이 있다는 것도 알리지 않는다")
    void hidesFromStranger() {
        assertThatThrownBy(() -> service.cancel(CONTRACT_ID, STRANGER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_NOT_FOUND);
    }

    @Test
    @DisplayName("환불 결과를 모르면 503 이지만 계약은 이미 취소돼 있다")
    void pendingRefundKeepsCancellation() {
        when(refunds.refundAll(CONTRACT_ID, CLIENT_ID)).thenReturn(RefundOutcome.PENDING);

        assertThatThrownBy(() -> service.cancel(CONTRACT_ID, CLIENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.REFUND_PENDING);
        assertThat(contract.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("이미 취소된 계약에 다시 부르면 환불만 잇는다")
    void repeatedCancelContinuesRefund() {
        contract.cancel();

        service.cancel(CONTRACT_ID, CLIENT_ID);

        assertThat(contract.isCancelled()).isTrue();
        verify(refunds).refundAll(CONTRACT_ID, CLIENT_ID);
    }
}
