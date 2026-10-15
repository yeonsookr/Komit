package com.a501.escrow.domain.escrow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.a501.escrow.common.crypto.FieldEncryption;
import com.a501.escrow.common.crypto.RequestFingerprint;
import com.a501.escrow.common.crypto.SensitiveDataHasher;
import com.a501.escrow.common.security.ApiRequirements;
import com.a501.escrow.common.security.AuthenticatedCaller;
import com.a501.escrow.common.security.InternalApiSecurity;
import com.a501.escrow.config.FinanceProperties;
import com.a501.escrow.domain.escrow.dto.EscrowDtos;
import com.a501.escrow.domain.escrow.repository.DepositEscrow;
import com.a501.escrow.domain.escrow.repository.EscrowDepositRepository;
import com.a501.escrow.domain.escrow.repository.EscrowSettlementRepository;
import com.a501.escrow.domain.member.repository.MemberAccountProvision;
import com.a501.escrow.domain.member.repository.MemberAccountRepository;
import com.a501.escrow.integration.finance.FinanceClient;
import org.junit.jupiter.api.Test;

class EscrowDepositServiceTest {
    @Test
    void createsVerifiesSettlesAndClosesDepositExactlyOnce() {
        InternalApiSecurity security = mock(InternalApiSecurity.class);
        EscrowDepositRepository repository = mock(EscrowDepositRepository.class);
        EscrowSettlementRepository settlements = mock(EscrowSettlementRepository.class);
        MemberAccountRepository members = mock(MemberAccountRepository.class);
        RequestFingerprint fingerprints = mock(RequestFingerprint.class);
        SensitiveDataHasher hasher = mock(SensitiveDataHasher.class);
        FieldEncryption encryption = mock(FieldEncryption.class);
        FinanceClient finance = mock(FinanceClient.class);
        FinanceProperties properties = new FinanceProperties(true, "https://finance.example", "api-key",
                "Asia/Seoul", "product", "escrow-key", "0010000000000000", "0010000000000001", 30);
        EscrowDepositService service = new EscrowDepositService(security, repository, settlements, members, fingerprints,
                hasher, encryption, finance, properties,
                Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC), "pod-1");
        EscrowRequestHeader header = new EscrowRequestHeader("createEscrow", "20260919", "210000",
                "00100", "001", "createEscrow", "20260919210000000001", "client-key", "deposit-key", "payer-key");
        EscrowDtos.CreateRequest request = new EscrowDtos.CreateRequest(header, "CONTRACT-1", "DEVELOPMENT",
                "김의뢰", BigInteger.valueOf(650_000), "001", "0012222222222222");
        MemberAccountProvision member = new MemberAccountProvision(9, 7, "payer@example.com", "product",
                "member-key", "f", "COMPLETED", "COMPLETED", OffsetDateTime.now(), "user-cipher",
                "001", "account-cipher", 50_000_000, "COMPLETED", "seed-ref", "seed-tx");
        DepositEscrow creating = state("VA_CREATING", "PENDING", "DEPOSIT_PENDING");
        DepositEscrow vaCreated = state("VA_CREATED", "PROCESSING", "DEPOSIT_PENDING");
        DepositEscrow sending = state("TRANSFER_SENDING", "PROCESSING", "DEPOSIT_PENDING");
        DepositEscrow transferred = state("TRANSFERRED", "PROCESSING", "DEPOSIT_PENDING");
        DepositEscrow verified = state("VERIFIED", "PROCESSING", "DEPOSIT_PENDING");
        DepositEscrow settled = state("SETTLED", "COMPLETED", "ACTIVE");
        DepositEscrow closed = state("VA_CLOSED", "COMPLETED", "ACTIVE");

        when(security.authenticate(header, "createEscrow", ApiRequirements.write(true)))
                .thenReturn(new AuthenticatedCaller.Client(7, "Komit"));
        when(fingerprints.create(request)).thenReturn("fingerprint");
        when(repository.findByIdempotencyKey(7, "deposit-key"))
                .thenReturn(Optional.empty(), Optional.of(creating));
        when(repository.findByContractId(7, "CONTRACT-1")).thenReturn(Optional.empty());
        when(hasher.userKeyLookupHash(7, "payer-key")).thenReturn("payer-hash");
        when(members.findCompletedByUserKeyHash(7, "payer-hash")).thenReturn(Optional.of(member));
        when(encryption.decrypt("account-cipher")).thenReturn("0012222222222222");
        when(encryption.encrypt("김의뢰")).thenReturn("name-cipher");
        when(encryption.encrypt("0012222222222222")).thenReturn("source-cipher");
        when(encryption.decrypt("name-cipher")).thenReturn("김의뢰");
        when(encryption.decrypt("source-cipher")).thenReturn("0012222222222222");
        when(repository.findById(7, 11)).thenReturn(Optional.of(creating), Optional.of(vaCreated),
                Optional.of(sending), Optional.of(transferred), Optional.of(verified),
                Optional.of(settled), Optional.of(closed));
        when(repository.prepareAttempt(anyLong(), anyString(), anyString())).thenReturn(101L, 102L, 103L, 104L, 105L);
        when(repository.claimAttempt(anyLong(), anyString())).thenReturn(true);
        when(repository.attemptReference(anyLong())).thenAnswer(invocation -> "20260919210000" +
                String.format("%06d", invocation.<Long>getArgument(0)));
        when(finance.createVirtualAccount(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new FinanceClient.VirtualAccount("va-1", "0011111111111111"));
        when(finance.transfer(anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn("transfer-1");
        when(finance.inquireVirtualAccountDeposits("escrow-key", "va-1"))
                .thenReturn(List.of(new FinanceClient.VirtualAccountDeposit(BigInteger.valueOf(650_000), true, false)));
        when(finance.settleVirtualAccount("escrow-key", "va-1", "20260919210000000104"))
                .thenReturn(new FinanceClient.VirtualAccountSettlement(BigInteger.valueOf(650_000), "settled"));
        when(finance.closeVirtualAccount("escrow-key", "va-1", "20260919210000000105")).thenReturn("CLOSED");

        EscrowDtos.AmountRecord result = service.create(request);

        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.depositedAmount()).isEqualTo(BigInteger.valueOf(650_000));
        verify(repository).settled(11, 21, BigInteger.valueOf(650_000));
        verify(repository).closed(21);
    }

    private static DepositEscrow state(String step, String transactionStatus, String escrowStatus) {
        return new DepositEscrow(11, 21, 7, "CONTRACT-1", "DEVELOPMENT", BigInteger.valueOf(650_000),
                escrowStatus, "NONE", transactionStatus, "deposit-key", "fingerprint", step, "name-cipher",
                "VA_CREATING".equals(step) ? null : "va-1", "VA_CREATING".equals(step) ? null : "0011111111111111",
                "COMPLETED".equals(transactionStatus) ? "MATCHED" : "PENDING", null, null, "001", "source-cipher");
    }
}
