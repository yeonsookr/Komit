package com.a501.service.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.domain.account.crypto.AccountDataCrypto;
import com.a501.service.domain.account.dto.DepositAccount;
import com.a501.service.domain.account.entity.MemberAccountProvision;
import com.a501.service.domain.account.repository.AccountRepository;
import com.a501.service.domain.account.repository.MemberAccountProvisionRepository;
import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.code.service.CodeQueryService;
import com.a501.service.domain.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 예치에서 돈이 빠지는 계좌를 화면에 보여 주는 규칙 (`R-PAY-09`). */
class MemberAccountServiceTest {
    private static final Integer USER_ID = 9;
    private static final int SSAFY_BANK = 58;

    private final MemberAccountProvisioningService provisioning = mock(MemberAccountProvisioningService.class);
    private final MemberAccountProvisionRepository provisions = mock(MemberAccountProvisionRepository.class);
    private final CodeQueryService codes = mock(CodeQueryService.class);
    private final MemberAccountService service = new MemberAccountService(
            mock(UserService.class),
            provisioning,
            provisions,
            new AccountQueryService(mock(AccountRepository.class), provisions, mock(AccountDataCrypto.class), codes));

    @BeforeEach
    void setUp() {
        MemberAccountProvision provision = MemberAccountProvision.pending(USER_ID, "key");
        ReflectionTestUtils.setField(provision, "id", 31L);
        when(provisions.findByUserId(USER_ID)).thenReturn(Optional.of(provision));
        when(codes.findCodes(List.of(SSAFY_BANK)))
                .thenReturn(Map.of(SSAFY_BANK, new CodeResponse(SSAFY_BANK, "싸피은행")));
    }

    @Test
    @DisplayName("발급 계좌를 은행 이름과 끝 네 자리로만 보여 준다")
    void masksTheIssuedAccount() {
        when(provisioning.provision(USER_ID)).thenReturn(
                new MemberAccountProvisioningService.MemberAccount("user-key", "001", "0011012345678842"));

        DepositAccount account = service.depositAccount(USER_ID);

        assertThat(account).isEqualTo(new DepositAccount(31L, "싸피은행 ···· 8842"));
        assertThat(account.maskedNumber()).doesNotContain("0011012345678842");
    }

    @Test
    @DisplayName("금융망에서 쓰지 않는 은행이면 끝 네 자리만 보여 준다")
    void omitsUnknownBankName() {
        when(provisioning.provision(USER_ID)).thenReturn(
                new MemberAccountProvisioningService.MemberAccount("user-key", "088", "1100123456780351"));

        assertThat(service.depositAccount(USER_ID).maskedNumber()).isEqualTo("···· 0351");
    }
}
