package com.a501.escrow.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.a501.escrow.common.crypto.FieldEncryption;
import com.a501.escrow.common.crypto.RequestFingerprint;
import com.a501.escrow.common.crypto.SensitiveDataHasher;
import com.a501.escrow.common.security.ApiRequirements;
import com.a501.escrow.common.security.AuthenticatedCaller;
import com.a501.escrow.common.security.InternalApiSecurity;
import com.a501.escrow.config.FinanceProperties;
import com.a501.escrow.domain.member.dto.MemberAccountDtos;
import com.a501.escrow.domain.member.repository.MemberAccountProvision;
import com.a501.escrow.domain.member.repository.MemberAccountRepository;
import com.a501.escrow.integration.finance.FinanceClient;
import org.junit.jupiter.api.Test;

class MemberAccountServiceTest {
    @Test
    void provisionsEachExternalStepOnceAndReplaysCompletedResult() {
        InternalApiSecurity security = mock(InternalApiSecurity.class);
        MemberAccountRepository repository = mock(MemberAccountRepository.class);
        RequestFingerprint fingerprint = mock(RequestFingerprint.class);
        SensitiveDataHasher hasher = mock(SensitiveDataHasher.class);
        FieldEncryption encryption = mock(FieldEncryption.class);
        FinanceClient finance = mock(FinanceClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC);
        MemberAccountService service = new MemberAccountService(security, repository, fingerprint, hasher,
                encryption, finance, new FinanceProperties(
                        false, "https://finance.example", "test-api-key", "Asia/Seoul", "product-1",
                        "unused", "0010000000000000", "0010000000000001", 30), clock);
        EscrowRequestHeader header = header("createMemberAccount", "member-key", null);
        MemberAccountDtos.CreateRequest request = new MemberAccountDtos.CreateRequest(header, "new@komit.example");
        MemberAccountProvision initial = provision("USER_REGISTER", null, null, null);
        MemberAccountProvision accountOpen = provision("ACCOUNT_OPEN", "key-cipher", null, null);
        MemberAccountProvision seed = provision("SEED_DEPOSIT", "key-cipher", "001", "account-cipher");
        MemberAccountProvision completed = new MemberAccountProvision(1, 7, "new@komit.example", "product-1",
                "member-key", "fingerprint", "COMPLETED", "COMPLETED", null, "key-cipher", "001",
                "account-cipher", 50_000_000L, "COMPLETED", "reference", "tx-seed");

        when(security.authenticate(header, "createMemberAccount", ApiRequirements.write(false)))
                .thenReturn(new AuthenticatedCaller.Client(7, "Komit"));
        when(fingerprint.create(request)).thenReturn("fingerprint");
        when(repository.findByKey(7, "member-key")).thenReturn(Optional.of(initial), Optional.of(completed));
        when(repository.findByUser(7, "new@komit.example"))
                .thenReturn(Optional.of(accountOpen), Optional.of(seed), Optional.of(completed));
        when(repository.claim(1, "USER_REGISTER")).thenReturn(true);
        when(repository.claim(1, "ACCOUNT_OPEN")).thenReturn(true);
        when(repository.claimSeed(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        when(finance.registerUser("new@komit.example"))
                .thenReturn(new FinanceClient.UserRegistration("new@komit.example", "user-key"));
        when(finance.openDemandDepositAccount("user-key", "product-1"))
                .thenReturn(new FinanceClient.DemandDepositAccount(
                        "001", "1234567890123456", "Komit 데모 입출금", "1", "KRW"));
        when(finance.depositSeedMoney(org.mockito.ArgumentMatchers.eq("user-key"),
                org.mockito.ArgumentMatchers.eq("1234567890123456"),
                org.mockito.ArgumentMatchers.eq(BigInteger.valueOf(50_000_000L)),
                org.mockito.ArgumentMatchers.anyString())).thenReturn("tx-seed");
        when(encryption.encrypt("user-key")).thenReturn("key-cipher");
        when(encryption.encrypt("1234567890123456")).thenReturn("account-cipher");
        when(encryption.decrypt("key-cipher")).thenReturn("user-key");
        when(encryption.decrypt("account-cipher")).thenReturn("1234567890123456");
        when(hasher.userKeyLookupHash(7, "user-key")).thenReturn("hash");

        assertThat(service.create(request).accountNo()).isEqualTo("1234567890123456");
        assertThat(service.create(request).accountNo()).isEqualTo("1234567890123456");
        verify(finance).registerUser("new@komit.example");
        verify(finance).openDemandDepositAccount("user-key", "product-1");
        verify(finance).depositSeedMoney(org.mockito.ArgumentMatchers.eq("user-key"),
                org.mockito.ArgumentMatchers.eq("1234567890123456"),
                org.mockito.ArgumentMatchers.eq(BigInteger.valueOf(50_000_000L)),
                org.mockito.ArgumentMatchers.anyString());
        verifyNoMoreInteractions(finance);
    }

    /**
     * 금융망 개설 응답은 상품명·계좌종류·통화를 담지 않을 수 있다. 담겨 오지 않은 값을
     * 틀렸다고 보면 정상 개설이 전부 막히므로, 은행코드만으로 저장까지 간다. (R-ACC-10)
     */
    @Test
    void opensTheAccountWhenTheResponseOmitsTheProductAttributes() {
        InternalApiSecurity security = mock(InternalApiSecurity.class);
        MemberAccountRepository repository = mock(MemberAccountRepository.class);
        RequestFingerprint fingerprint = mock(RequestFingerprint.class);
        SensitiveDataHasher hasher = mock(SensitiveDataHasher.class);
        FieldEncryption encryption = mock(FieldEncryption.class);
        FinanceClient finance = mock(FinanceClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC);
        MemberAccountService service = new MemberAccountService(security, repository, fingerprint, hasher,
                encryption, finance, new FinanceProperties(
                        false, "https://finance.example", "test-api-key", "Asia/Seoul", "product-1",
                        "unused", "0010000000000000", "0010000000000001", 30), clock);
        EscrowRequestHeader header = header("createMemberAccount", "member-key", null);
        MemberAccountDtos.CreateRequest request = new MemberAccountDtos.CreateRequest(header, "new@komit.example");
        MemberAccountProvision accountOpen = provision("ACCOUNT_OPEN", "key-cipher", null, null);
        MemberAccountProvision seed = provision("SEED_DEPOSIT", "key-cipher", "001", "account-cipher");
        MemberAccountProvision completed = new MemberAccountProvision(1, 7, "new@komit.example", "product-1",
                "member-key", "fingerprint", "COMPLETED", "COMPLETED", null, "key-cipher", "001",
                "account-cipher", 50_000_000L, "COMPLETED", "reference", "tx-seed");

        when(security.authenticate(header, "createMemberAccount", ApiRequirements.write(false)))
                .thenReturn(new AuthenticatedCaller.Client(7, "Komit"));
        when(fingerprint.create(request)).thenReturn("fingerprint");
        when(repository.findByKey(7, "member-key")).thenReturn(Optional.of(accountOpen));
        when(repository.findByUser(7, "new@komit.example"))
                .thenReturn(Optional.of(seed), Optional.of(completed));
        when(repository.claim(1, "ACCOUNT_OPEN")).thenReturn(true);
        when(repository.claimSeed(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        when(finance.openDemandDepositAccount("user-key", "product-1"))
                .thenReturn(new FinanceClient.DemandDepositAccount(
                        "001", "1234567890123456", null, null, null));
        when(finance.depositSeedMoney(org.mockito.ArgumentMatchers.eq("user-key"),
                org.mockito.ArgumentMatchers.eq("1234567890123456"),
                org.mockito.ArgumentMatchers.eq(BigInteger.valueOf(50_000_000L)),
                org.mockito.ArgumentMatchers.anyString())).thenReturn("tx-seed");
        when(encryption.encrypt("1234567890123456")).thenReturn("account-cipher");
        when(encryption.decrypt("key-cipher")).thenReturn("user-key");
        when(encryption.decrypt("account-cipher")).thenReturn("1234567890123456");

        assertThat(service.create(request).accountNo()).isEqualTo("1234567890123456");
        verify(repository).accountOpened(1, "001", "account-cipher");
    }

    private static MemberAccountProvision provision(String step, String key, String bank, String account) {
        return new MemberAccountProvision(1, 7, "new@komit.example", "product-1", "member-key",
                "fingerprint", step, "PENDING", null, key, bank, account, 50_000_000L,
                "PENDING", null, null);
    }

    private static EscrowRequestHeader header(String api, String idempotency, String userKey) {
        return new EscrowRequestHeader(api, "20260919", "120000", "00100", "001", api,
                "20260919120000000001", "client-key", idempotency, userKey);
    }
}
