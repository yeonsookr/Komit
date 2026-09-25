package com.a501.service.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.domain.account.crypto.AccountDataCrypto;
import com.a501.service.domain.account.dto.PayoutAccount;
import com.a501.service.domain.account.entity.Account;
import com.a501.service.domain.account.entity.MemberAccountProvision;
import com.a501.service.domain.account.repository.AccountRepository;
import com.a501.service.domain.account.repository.MemberAccountProvisionRepository;
import com.a501.service.domain.code.service.CodeQueryService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 대금을 받을 계좌를 고르는 규칙 (`R-PAY-14`, `R-ACC-10`). */
class AccountQueryServicePayoutTest {
    private static final Integer USER_ID = 9;
    private static final int SSAFY_BANK = 58;

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final MemberAccountProvisionRepository provisions = mock(MemberAccountProvisionRepository.class);
    private final AccountDataCrypto crypto = mock(AccountDataCrypto.class);
    private final AccountQueryService service =
            new AccountQueryService(accounts, provisions, crypto, mock(CodeQueryService.class));

    @BeforeEach
    void setUp() {
        when(accounts.findFirstByUserIdAndDeletedFalseOrderByIdDesc(USER_ID))
                .thenReturn(Optional.of(Account.register(USER_ID, "qwer", "···· 0351", SSAFY_BANK, "sealed")));
        when(crypto.decrypt("sealed")).thenReturn("0011234567890351");
        when(provisions.findByUserId(USER_ID))
                .thenReturn(Optional.of(MemberAccountProvision.pending(USER_ID, "key")));
    }

    @Test
    @DisplayName("등록 계좌 번호에, 적어 둔 예금주가 아니라 금융망 예금주명을 붙인다")
    void usesFinanceHolderName() {
        assertThat(service.findPayoutAccount(USER_ID))
                .contains(new PayoutAccount("001", "0011234567890351", "komit-9"));
    }

    @Test
    @DisplayName("등록 계좌가 없으면 비어 있다")
    void emptyWithoutRegisteredAccount() {
        when(accounts.findFirstByUserIdAndDeletedFalseOrderByIdDesc(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.findPayoutAccount(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("금융망에 없는 은행의 계좌면 비어 있다")
    void emptyForOtherBank() {
        when(accounts.findFirstByUserIdAndDeletedFalseOrderByIdDesc(USER_ID))
                .thenReturn(Optional.of(Account.register(USER_ID, "qwer", "···· 0351", 52, "sealed")));

        assertThat(service.findPayoutAccount(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("회원 금융 계정이 없으면 예금주명을 알 수 없어 비어 있다")
    void emptyWithoutProvision() {
        when(provisions.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.findPayoutAccount(USER_ID)).isEmpty();
    }
}
