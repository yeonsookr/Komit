package com.a501.service.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.account.AccountErrorCode;
import com.a501.service.domain.account.client.EscrowAccountClient;
import com.a501.service.domain.account.crypto.AccountDataCrypto;
import org.junit.jupiter.api.Test;

class MemberAccountProvisioningServiceTest {
    private static final String KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    private final MemberAccountStore store = mock(MemberAccountStore.class);
    private final EscrowAccountClient escrow = mock(EscrowAccountClient.class);
    private final MemberAccountProvisioningService service =
            new MemberAccountProvisioningService(store, escrow, new AccountDataCrypto(KEY));

    /** R-ACC-13: 가입을 중간에 그만둔 회원이 다시 들어와도 계좌를 새로 만들지 않는다. */
    @Test
    void reusesTheIssuedAccountWithoutCallingEscrowAgain() {
        AccountDataCrypto crypto = new AccountDataCrypto(KEY);
        when(store.prepare(7)).thenReturn(new MemberAccountStore.Pending(
                "komit-7@komit.local", "idempotency-7", crypto.encrypt("user-key-7"), "001",
                crypto.encrypt("0011012345678842")));

        MemberAccountProvisioningService.MemberAccount account = service.provision(7);

        assertThat(account).isEqualTo(new MemberAccountProvisioningService.MemberAccount(
                "user-key-7", "001", "0011012345678842"));
        verify(escrow, never()).createMemberAccount(anyString(), anyString());
        verify(store, never()).complete(any(), any(), any(), any());
    }

    @Test
    void issuesOnceAndKeepsWhatEscrowReturned() {
        when(store.prepare(8)).thenReturn(new MemberAccountStore.Pending(
                "komit-8@komit.local", "idempotency-8", null, null, null));
        when(escrow.createMemberAccount("komit-8@komit.local", "idempotency-8"))
                .thenReturn(new EscrowAccountClient.MemberAccount(
                        "user-key-8", "001", "0011012345678843"));

        MemberAccountProvisioningService.MemberAccount account = service.provision(8);

        assertThat(account.accountNo()).isEqualTo("0011012345678843");
        verify(store).complete(any(), anyString(), anyString(), anyString());
        verify(store, never()).fail(any());
    }

    /** 발급이 실패하면 상태를 남긴다. 행과 멱등 키는 그대로 두어 다음 시도가 이어받는다. */
    @Test
    void marksTheProvisionFailedWhenEscrowRejects() {
        when(store.prepare(9)).thenReturn(new MemberAccountStore.Pending(
                "komit-9@komit.local", "idempotency-9", null, null, null));
        when(escrow.createMemberAccount(anyString(), anyString()))
                .thenThrow(new BusinessException(AccountErrorCode.ESCROW_UNAVAILABLE));

        assertThatThrownBy(() -> service.provision(9)).isInstanceOf(BusinessException.class);

        verify(store).fail(9);
        verify(store, never()).complete(any(), any(), any(), any());
    }
}
