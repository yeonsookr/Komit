package com.a501.escrow.domain.finance.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.a501.escrow.config.AppProperties;
import com.a501.escrow.config.FinanceProperties;
import com.a501.escrow.config.InstanceRole;
import com.a501.escrow.domain.escrow.repository.EscrowSettlementRepository;
import com.a501.escrow.domain.escrow.repository.EscrowSettlementRepository.UnknownTransfer;
import com.a501.escrow.integration.finance.FinanceClient;
import org.junit.jupiter.api.Test;

class FinancialRecoveryServiceTest {
    @Test
    void resolvesUnknownTransferFromExactSummaryAndAmountAndAuditsEvidence() {
        EscrowSettlementRepository repository = mock(EscrowSettlementRepository.class);
        FinanceClient finance = mock(FinanceClient.class);
        UnknownTransfer unknown = unknown();
        when(repository.unknownTransfers()).thenReturn(List.of(unknown));
        when(repository.prepareAttempt(eq(20L), eq("RESULT_QUERY"), anyString())).thenReturn(30L);
        when(repository.claimAttempt(30L, "pod-1")).thenReturn(true);
        when(finance.inquireTransactions(eq("escrow-key"), eq("0010000000000000"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq("D"), eq("DESC")))
                .thenReturn(List.of(new FinanceClient.AccountTransaction("bank-tx", "20260920", "120000",
                        "D", "출금", "4000", "0", "KOMIT-FEE-20")));

        service(repository, finance, InstanceRole.WRITER).recoverUnknownTransfers();

        verify(repository).resolveUnknown(20L, 30L, "bank-tx");
        verify(repository).refreshSettlement(10L, 1L);
        verify(repository).auditRecovery(unknown, "UNKNOWN_RESOLVED", "bank-tx", "COMPLETED");
    }

    @Test
    void preservesUnknownWhenNoMatchingBankTransactionExistsAndReaderDoesNothing() {
        EscrowSettlementRepository repository = mock(EscrowSettlementRepository.class);
        FinanceClient finance = mock(FinanceClient.class);
        UnknownTransfer unknown = unknown();
        when(repository.unknownTransfers()).thenReturn(List.of(unknown));
        when(repository.prepareAttempt(eq(20L), eq("RESULT_QUERY"), anyString())).thenReturn(30L);
        when(repository.claimAttempt(30L, "pod-1")).thenReturn(true);
        when(finance.inquireTransactions(eq("escrow-key"), eq("0010000000000000"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq("D"), eq("DESC")))
                .thenReturn(List.of());

        service(repository, finance, InstanceRole.WRITER).recoverUnknownTransfers();

        verify(repository).finishAttempt(30L, "FAILED", null, "NOT_FOUND");
        verify(repository).auditRecovery(unknown, "UNKNOWN_UNRESOLVED", "QUERY:30", "UNKNOWN");

        EscrowSettlementRepository readerRepository = mock(EscrowSettlementRepository.class);
        service(readerRepository, finance, InstanceRole.READER).recoverUnknownTransfers();
        verify(readerRepository, org.mockito.Mockito.never()).unknownTransfers();
    }

    private static FinancialRecoveryService service(EscrowSettlementRepository repository,
            FinanceClient finance, InstanceRole role) {
        FinanceProperties properties = new FinanceProperties(true, "https://finance.example", "api-key",
                "Asia/Seoul", "product", "escrow-key", "0010000000000000", "0010000000000001", 30);
        return new FinancialRecoveryService(repository, finance, properties,
                new AppProperties(URI.create("http://localhost:8081"), role),
                Clock.fixed(Instant.parse("2026-09-20T03:00:00Z"), ZoneOffset.UTC), "pod-1");
    }

    private static UnknownTransfer unknown() {
        return new UnknownTransfer(20, 10, 1, "FEE", BigInteger.valueOf(4_000),
                OffsetDateTime.parse("2026-09-20T10:00:00+09:00"));
    }
}
