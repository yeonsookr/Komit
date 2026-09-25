package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.account.service.MemberAccountProvisioningService;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.client.EscrowDepositClient;
import com.a501.service.domain.contract.dto.request.DepositRequest;
import com.a501.service.domain.contract.dto.response.DepositResponse;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import com.a501.service.domain.user.service.UserQueryService;
import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 예치 규칙을 DB 와 Escrow 없이 검증합니다.
 *
 * <p>예치는 의뢰인만 걸고, 같은 계약에 두 번 걸지 않으며, 돈은 회원가입 때 발급한 계좌에서 빠집니다.
 * (`R-PAY-05`, `R-PAY-09`)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractDepositServiceTest {
    private static final Integer ROOM_ID = 92;
    private static final Integer CONTRACT_ID = 120;
    private static final Integer CLIENT_ID = 9001;
    private static final Integer DEVELOPER_USER_ID = 9101;
    private static final Integer STRANGER_ID = 9999;
    private static final DepositRequest REQUEST = new DepositRequest(9, "dep-120-9f2c");

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ConsultingAgreementRepository consultingAgreementRepository;
    @Mock
    private DevelopmentContractRepository developmentContractRepository;
    @Mock
    private RoomQueryService roomQueryService;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private MemberAccountProvisioningService accountProvisioning;
    @Mock
    private EscrowDepositClient escrowDepositClient;

    @InjectMocks
    private ContractDepositService service;

    private Contract contract;

    @BeforeEach
    void setUp() {
        contract = Contract.of(ROOM_ID, Contract.ContractType.CONSULTING);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        // 예치는 의뢰인이 서명한 계약에만 건다. 서명 전 경우는 따로 본다.
        contract.sign(true, 1, null);

        ConsultingAgreement agreement =
                ConsultingAgreement.of(CONTRACT_ID, "회원탈퇴 경로를 봅니다", 50_000);

        when(contractRepository.findByIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(contract));
        when(roomQueryService.findParties(ROOM_ID))
                .thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_USER_ID)));
        when(consultingAgreementRepository.findByContractIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(agreement));
        when(userQueryService.findName(CLIENT_ID)).thenReturn(Optional.of("박다빈"));
        when(accountProvisioning.provision(CLIENT_ID))
                .thenReturn(new MemberAccountProvisioningService.MemberAccount(
                        "user-key", "001", "0011234567890123"));
        when(escrowDepositClient.findByContract("120")).thenReturn(Optional.empty());
        when(escrowDepositClient.create(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EscrowDepositClient.Deposit(
                        77L, "120", "DEPOSITED", BigInteger.valueOf(50_000), BigInteger.ZERO));
    }

    @Test
    @DisplayName("의뢰인이 걸면 계약 금액이 Escrow 로 넘어간다")
    void sendsAmountToEscrow() {
        DepositResponse response = service.deposit(CONTRACT_ID, CLIENT_ID, REQUEST);

        ArgumentCaptor<BigInteger> amount = ArgumentCaptor.forClass(BigInteger.class);
        verify(escrowDepositClient).create(
                eq("120"), eq("CONSULTING"), eq("박다빈"), amount.capture(), eq("user-key"),
                eq("001"), eq("0011234567890123"), eq("dep-120-9f2c"));
        assertThat(amount.getValue()).isEqualTo(BigInteger.valueOf(50_000));
        assertThat(response.status()).isEqualTo("DEPOSITED");
        assertThat(response.amount()).isEqualTo(BigInteger.valueOf(50_000));
    }

    @Test
    @DisplayName("돈은 회원가입 때 발급한 계좌에서 빠진다")
    void withdrawsFromProvisionedAccount() {
        service.deposit(CONTRACT_ID, CLIENT_ID, REQUEST);

        // 계좌를 따로 고르지 않는다. (`R-PAY-09`)
        verify(accountProvisioning).provision(CLIENT_ID);
    }

    @Test
    @DisplayName("예치는 의뢰인만 건다")
    void onlyClientDeposits() {
        assertThatThrownBy(() -> service.deposit(CONTRACT_ID, DEVELOPER_USER_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.FORBIDDEN);
        verify(escrowDepositClient, never())
                .create(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("의뢰인이 서명하기 전에는 걸지 않는다. 개발자만 서명했어도 마찬가지다 (R-CON-20)")
    void rejectsBeforeClientSigns() {
        Contract draft = Contract.of(ROOM_ID, Contract.ContractType.CONSULTING);
        ReflectionTestUtils.setField(draft, "id", CONTRACT_ID);
        when(contractRepository.findByIdAndDeletedFalse(CONTRACT_ID)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.deposit(CONTRACT_ID, CLIENT_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_NOT_SIGNED);

        draft.sign(false, 2, null);
        assertThatThrownBy(() -> service.deposit(CONTRACT_ID, CLIENT_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_NOT_SIGNED);
        verify(escrowDepositClient, never())
                .create(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("의뢰인이 서명했으면 개발자 서명을 기다리지 않고 건다. 서명과 결제가 한 버튼이다 (R-CON-20)")
    void depositsOnceClientSigned() {
        Contract draft = Contract.of(ROOM_ID, Contract.ContractType.CONSULTING);
        ReflectionTestUtils.setField(draft, "id", CONTRACT_ID);
        draft.sign(true, 1, null);
        when(contractRepository.findByIdAndDeletedFalse(CONTRACT_ID)).thenReturn(Optional.of(draft));

        assertThat(service.deposit(CONTRACT_ID, CLIENT_ID, REQUEST).status()).isEqualTo("DEPOSITED");
    }

    @Test
    @DisplayName("같은 계약에 두 번 걸지 않는다")
    void rejectsSecondDeposit() {
        when(escrowDepositClient.findByContract("120"))
                .thenReturn(Optional.of(new EscrowDepositClient.Deposit(
                        77L, "120", "DEPOSITED", BigInteger.valueOf(50_000), BigInteger.ZERO)));

        assertThatThrownBy(() -> service.deposit(CONTRACT_ID, CLIENT_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.ALREADY_DEPOSITED);
        verify(escrowDepositClient, never())
                .create(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("당사자가 아니면 계약이 있다는 것도 알리지 않는다")
    void hidesContractFromStranger() {
        assertThatThrownBy(() -> service.deposit(CONTRACT_ID, STRANGER_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_NOT_FOUND);
    }

    @Test
    @DisplayName("걸 금액이 없으면 넘기지 않는다")
    void rejectsZeroAmount() {
        when(consultingAgreementRepository.findByContractIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(ConsultingAgreement.of(CONTRACT_ID, "주제", 0)));

        assertThatThrownBy(() -> service.deposit(CONTRACT_ID, CLIENT_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_REQUEST);
        verify(escrowDepositClient, never())
                .create(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("상태는 Escrow 에 물어 그대로 옮긴다")
    void readsStatusFromEscrow() {
        when(escrowDepositClient.findByContract("120"))
                .thenReturn(Optional.of(new EscrowDepositClient.Deposit(
                        77L, "120", "DEPOSIT_PENDING", BigInteger.ZERO, BigInteger.ZERO)));

        DepositResponse response = service.getDeposit(CONTRACT_ID, CLIENT_ID);

        assertThat(response.status()).isEqualTo("DEPOSIT_PENDING");
        assertThat(response.contractId()).isEqualTo(CONTRACT_ID);
    }

    @Test
    @DisplayName("개발자도 예치 상태는 읽는다")
    void developerMayReadStatus() {
        when(escrowDepositClient.findByContract("120"))
                .thenReturn(Optional.of(new EscrowDepositClient.Deposit(
                        77L, "120", "DEPOSITED", BigInteger.valueOf(50_000), BigInteger.ZERO)));

        // 돈은 의뢰인이 내지만 걸렸는지는 개발자도 알아야 한다.
        assertThat(service.getDeposit(CONTRACT_ID, DEVELOPER_USER_ID).status())
                .isEqualTo("DEPOSITED");
    }

    @Test
    @DisplayName("아직 걸지 않았으면 404 다")
    void rejectsMissingDeposit() {
        assertThatThrownBy(() -> service.getDeposit(CONTRACT_ID, CLIENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_NOT_FOUND);
    }
}
