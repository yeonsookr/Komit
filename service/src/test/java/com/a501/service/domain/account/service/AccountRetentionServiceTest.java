package com.a501.service.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.domain.account.entity.Account;
import com.a501.service.domain.account.entity.MemberAccountProvision;
import com.a501.service.domain.account.repository.AccountRepository;
import com.a501.service.domain.account.repository.AccountVerificationRepository;
import com.a501.service.domain.account.repository.MemberAccountProvisionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** R-ACC-06: 탈퇴 회원 계좌의 명의·번호와 금융망 사용자 키를 지우고, 가린 번호와 발급 상태는 남깁니다. */
@ExtendWith(MockitoExtension.class)
class AccountRetentionServiceTest {
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private MemberAccountProvisionRepository provisionRepository;
    @Mock
    private AccountVerificationRepository verificationRepository;
    @InjectMocks
    private AccountRetentionService service;

    @Test
    @DisplayName("계좌 명의·번호, 금융망 사용자 키와 계좌번호, 1원 인증 기록을 지운다")
    void anonymizesAccountData() {
        Account current = Account.register(12, "김미미", "···· 4412", 58, "enc-account");
        Account replaced = Account.register(12, "김미미", "···· 1234", 58, "enc-old");
        MemberAccountProvision provision = MemberAccountProvision.pending(12, "idem-12");
        provision.complete("enc-user-key", "001", "enc-member-account");
        when(accountRepository.findByUserId(12)).thenReturn(List.of(current, replaced));
        when(provisionRepository.findWithLockByUserId(12)).thenReturn(Optional.of(provision));

        service.anonymize(12);

        for (Account account : List.of(current, replaced)) {
            assertThat(account.getAccountHolder()).isEqualTo("탈퇴한 사용자");
            assertThat(account.getAccountNoEncrypted()).isNull();
        }
        assertThat(current.getMaskedNumber()).isEqualTo("···· 4412");
        assertThat(provision.getUserKeyEncrypted()).isNull();
        assertThat(provision.getAccountNoEncrypted()).isNull();
        assertThat(provision.getStatus()).isEqualTo("ISSUED");
        verify(verificationRepository).deleteByUserId(12);
    }

    @Test
    @DisplayName("계좌가 하나도 없는 회원도 실패하지 않는다")
    void noAccountIsFine() {
        when(accountRepository.findByUserId(12)).thenReturn(List.of());
        when(provisionRepository.findWithLockByUserId(12)).thenReturn(Optional.empty());

        service.anonymize(12);

        verify(verificationRepository).deleteByUserId(12);
    }
}
