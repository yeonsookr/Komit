package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.exception.ErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.client.EscrowDepositClient;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 착수의 조건과 상태 전이를 본다. */
class DevelopmentStartServiceTest {
    private static final int CONTRACT_ID = 130;
    private static final int DEVELOPMENT_ID = 51;
    private static final int ROOM_ID = 93;
    private static final int CLIENT_ID = 7;
    private static final int DEVELOPER_ID = 12;

    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final DevelopmentContractRepository developmentContractRepository = mock(DevelopmentContractRepository.class);
    private final MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
    private final RoomQueryService roomQueryService = mock(RoomQueryService.class);
    private final EscrowDepositClient escrowDepositClient = mock(EscrowDepositClient.class);
    private final ContractDetailService contractDetailService = mock(ContractDetailService.class);

    private final DevelopmentStartService service = new DevelopmentStartService(
            contractRepository, developmentContractRepository, milestoneRepository, roomQueryService,
            escrowDepositClient, contractDetailService);

    private Contract contract;
    private DevelopmentContract development;
    private List<Milestone> milestones;

    @BeforeEach
    void setUp() {
        contract = contract(true);
        when(contractRepository.findWithLockByIdAndDeletedFalse(CONTRACT_ID)).thenAnswer(invocation -> Optional.of(contract));
        when(roomQueryService.findParties(ROOM_ID)).thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_ID)));
        development = DevelopmentContract.of(CONTRACT_ID, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14);
        ReflectionTestUtils.setField(development, "id", DEVELOPMENT_ID);
        when(developmentContractRepository.findByContractIdAndDeletedFalse(CONTRACT_ID)).thenReturn(Optional.of(development));
        milestones = List.of(milestone(false), milestone(false), milestone(true));
        when(milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(DEVELOPMENT_ID))
                .thenReturn(milestones);
        givenDeposited(BigInteger.valueOf(650000));
    }

    @Test
    @DisplayName("착수하면 개발 쪽이 진행 중이 되고 1차 작업만 시작하며 나머지는 뒤로 잡힌다")
    void startsFirstWorkAndSchedulesTheRest() {
        service.start(CONTRACT_ID, DEVELOPER_ID);

        assertThat(development.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(milestones).extracting(Milestone::getStatus)
                .containsExactly("IN_PROGRESS", "SCHEDULED", "SCHEDULED");
        assertThat(development.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 26));
        verify(contractRepository).findWithLockByIdAndDeletedFalse(CONTRACT_ID);
        verify(contractDetailService).getContract(DEVELOPER_ID, CONTRACT_ID);
    }

    @Test
    @DisplayName("인계 작업이 앞에 있어도 1차는 첫 일반 작업이다")
    void firstWorkIsTheFirstRegularMilestone() {
        milestones = List.of(milestone(true), milestone(false));
        when(milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(DEVELOPMENT_ID))
                .thenReturn(milestones);

        service.start(CONTRACT_ID, DEVELOPER_ID);

        assertThat(milestones).extracting(Milestone::getStatus).containsExactly("SCHEDULED", "IN_PROGRESS");
    }

    @Test
    @DisplayName("예치가 끝나지 않았으면 착수하지 않는다")
    void needsDeposit() {
        givenDeposited(BigInteger.ZERO);
        assertCode(() -> service.start(CONTRACT_ID, DEVELOPER_ID), ContractErrorCode.DEPOSIT_REQUIRED);
        when(escrowDepositClient.findByContract(String.valueOf(CONTRACT_ID))).thenReturn(Optional.empty());
        assertCode(() -> service.start(CONTRACT_ID, DEVELOPER_ID), ContractErrorCode.DEPOSIT_REQUIRED);

        assertThat(development.getStatus()).isEqualTo("SIGNED");
    }

    @Test
    @DisplayName("이미 착수했으면 다시 착수하지 않는다")
    void startsOnce() {
        service.start(CONTRACT_ID, DEVELOPER_ID);

        assertCode(() -> service.start(CONTRACT_ID, DEVELOPER_ID), ContractErrorCode.ALREADY_STARTED);
    }

    @Test
    @DisplayName("양쪽이 서명하기 전이거나 취소된 계약이면 착수하지 않는다")
    void needsConcludedLiveContract() {
        contract = contract(false);
        assertCode(() -> service.start(CONTRACT_ID, DEVELOPER_ID), ContractErrorCode.CONTRACT_NOT_CONCLUDED);
        contract.cancel();
        assertCode(() -> service.start(CONTRACT_ID, DEVELOPER_ID), ContractErrorCode.CONTRACT_CANCELLED);

        verify(escrowDepositClient, never()).findByContract(String.valueOf(CONTRACT_ID));
    }

    @Test
    @DisplayName("의뢰인은 착수하지 않고, 당사자가 아니거나 개발 계약이 아니면 계약이 없다고 답한다")
    void onlyDeveloperOfDevelopmentContract() {
        assertCode(() -> service.start(CONTRACT_ID, CLIENT_ID), CommonErrorCode.FORBIDDEN);
        assertCode(() -> service.start(CONTRACT_ID, 99), ContractErrorCode.CONTRACT_NOT_FOUND);
        when(developmentContractRepository.findByContractIdAndDeletedFalse(CONTRACT_ID)).thenReturn(Optional.empty());
        assertCode(() -> service.start(CONTRACT_ID, DEVELOPER_ID), ContractErrorCode.CONTRACT_NOT_FOUND);
    }

    private void givenDeposited(BigInteger amount) {
        when(escrowDepositClient.findByContract(String.valueOf(CONTRACT_ID))).thenReturn(Optional.of(
                new EscrowDepositClient.Deposit(9, String.valueOf(CONTRACT_ID), "ACTIVE", amount, amount)));
    }

    private static Milestone milestone(boolean handover) {
        return Milestone.of(DEVELOPMENT_ID, 312, null, null, handover ? 0 : 100000, null, handover);
    }

    private static Contract contract(boolean concluded) {
        Contract contract = Contract.of(ROOM_ID, Contract.ContractType.DEVELOPMENT);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        if (concluded) {
            ReflectionTestUtils.setField(contract, "status", "SIGNED");
        }
        return contract;
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
