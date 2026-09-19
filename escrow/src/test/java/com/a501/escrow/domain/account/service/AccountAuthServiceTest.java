package com.a501.escrow.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.a501.escrow.common.crypto.FieldEncryption;
import com.a501.escrow.common.crypto.SensitiveDataHasher;
import com.a501.escrow.common.security.ApiRequirements;
import com.a501.escrow.common.security.AuthenticatedCaller;
import com.a501.escrow.common.security.InternalApiSecurity;
import com.a501.escrow.common.security.ServiceClientRepository;
import com.a501.escrow.domain.account.dto.AccountAuthDtos;
import com.a501.escrow.domain.account.repository.AccountAuthRepository;
import com.a501.escrow.domain.account.repository.AccountAuthRequest;
import com.a501.escrow.integration.finance.FinanceClient;
import org.junit.jupiter.api.Test;

class AccountAuthServiceTest {
    @Test
    void acceptsAnyFourDigitsForTheSameUnexpiredRequest() {
        InternalApiSecurity security = mock(InternalApiSecurity.class);
        AccountAuthRepository repository = mock(AccountAuthRepository.class);
        SensitiveDataHasher hasher = mock(SensitiveDataHasher.class);
        FieldEncryption encryption = mock(FieldEncryption.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC);
        AccountAuthService service = new AccountAuthService(security, repository,
                mock(ServiceClientRepository.class), hasher, encryption, mock(FinanceClient.class), clock, "test");
        EscrowRequestHeader header = new EscrowRequestHeader("checkAuthCode", "20260919", "120000",
                "00100", "001", "checkAuthCode", "20260919120000000001", "client-key", null, "user-key");
        AccountAuthDtos.CheckRequest request = new AccountAuthDtos.CheckRequest(
                "tx-auth", header, "1234567890123456", "9876");
        AccountAuthRequest stored = new AccountAuthRequest(1, 7, "fingerprint", "user-hash",
                "key-cipher", "account-cipher", "SUCCEEDED", "tx-auth", OffsetDateTime.now(clock).plusMinutes(1));

        when(security.authenticate(header, "checkAuthCode", new ApiRequirements(false, true, false, false)))
                .thenReturn(new AuthenticatedCaller.Client(7, "Komit"));
        when(hasher.userKeyLookupHash(7, "user-key")).thenReturn("user-hash");
        when(repository.findByTransactionNo(7, "tx-auth")).thenReturn(Optional.of(stored));
        when(encryption.decrypt("account-cipher")).thenReturn("1234567890123456");

        assertThat(service.check(request).status()).isEqualTo("SUCCESS");
    }
}
