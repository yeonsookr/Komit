package com.a501.escrow.domain.escrow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Clock;
import java.util.Optional;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.a501.escrow.common.crypto.FieldEncryption;
import com.a501.escrow.common.crypto.RequestFingerprint;
import com.a501.escrow.common.security.AuthenticatedCaller;
import com.a501.escrow.common.security.InternalApiSecurity;
import com.a501.escrow.config.FinanceProperties;
import com.a501.escrow.domain.escrow.dto.EscrowSettlementDtos;
import com.a501.escrow.domain.escrow.repository.EscrowRepository;
import com.a501.escrow.domain.escrow.repository.EscrowSettlementRepository;
import com.a501.escrow.domain.escrow.repository.SettlementTransaction;
import com.a501.escrow.integration.finance.FinanceClient;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class EscrowSettlementServiceTest {

    @Test
    void doesNotTransferBeforeAnotherPodFinishesHolderVerification() {
        InternalApiSecurity security = mock(InternalApiSecurity.class);
        EscrowSettlementRepository settlements = mock(EscrowSettlementRepository.class);
        RequestFingerprint fingerprints = mock(RequestFingerprint.class);
        FinanceClient finance = mock(FinanceClient.class);
        EscrowRequestHeader header = new EscrowRequestHeader(
                "releaseEscrow", "20260920", "120000", "00100", "001", "releaseEscrow",
                "20260920120000000001", "client-key", "release-key", null);
        var request = new EscrowSettlementDtos.ReleaseRequest(header, 1, "SETTLEMENT:1",
                BigInteger.valueOf(100_000), "001", "0011111111111111", "worker");
        var payout = new SettlementTransaction(10, 20, 1, "RELEASE", BigInteger.valueOf(99_000),
                "PENDING", "SETTLEMENT:1", "release-key", "fingerprint", "001",
                "encrypted-account", "worker", null);

        when(security.authenticate(any(), anyString(), any())).thenReturn(new AuthenticatedCaller.Client(1, "service"));
        when(fingerprints.create(request)).thenReturn("fingerprint");
        when(settlements.findByKey(1, "RELEASE", "release-key")).thenReturn(Optional.of(payout));
        when(settlements.findById(10)).thenReturn(Optional.of(payout));
        when(settlements.prepareAttempt(eq(10L), eq("HOLDER_VERIFY"), anyString())).thenReturn(Optional.empty());
        when(settlements.completedAmount(20, "RELEASE")).thenReturn(BigInteger.ZERO);
        when(settlements.completedAmount(20, "FEE")).thenReturn(BigInteger.ZERO);
        when(settlements.settlementStatus(20)).thenReturn("PROCESSING");
        when(settlements.balance(1)).thenReturn(BigInteger.valueOf(100_000));

        var service = new EscrowSettlementService(security, mock(EscrowRepository.class), settlements,
                fingerprints, mock(FieldEncryption.class), finance,
                new FinanceProperties(true, "https://finance.example", "api-key", "Asia/Seoul",
                        "product", "escrow-user", "0010000000000001", "0010000000000002", 30),
                Clock.systemUTC(), mock(PlatformTransactionManager.class), "pod-b");

        var result = service.release(request);

        assertThat(result.status()).isEqualTo("PROCESSING");
        verify(settlements, never()).processing(10);
        verify(finance, never()).inquireAccountHolder(anyString(), anyString());
        verify(finance, never()).transfer(anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString());
    }
}
