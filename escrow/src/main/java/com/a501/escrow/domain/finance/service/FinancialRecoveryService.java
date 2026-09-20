package com.a501.escrow.domain.finance.service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.a501.escrow.config.AppProperties;
import com.a501.escrow.config.FinanceProperties;
import com.a501.escrow.config.InstanceRole;
import com.a501.escrow.domain.escrow.repository.EscrowSettlementRepository;
import com.a501.escrow.domain.escrow.repository.EscrowSettlementRepository.UnknownTransfer;
import com.a501.escrow.integration.finance.FinanceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FinancialRecoveryService {
    private static final DateTimeFormatter REFERENCE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EscrowSettlementRepository repository;
    private final FinanceClient finance;
    private final FinanceProperties financeProperties;
    private final AppProperties appProperties;
    private final Clock clock;
    private final ZoneId financeZone;
    private final String instanceId;

    public FinancialRecoveryService(EscrowSettlementRepository repository, FinanceClient finance,
            FinanceProperties financeProperties, AppProperties appProperties, Clock clock,
            @Value("${POD_NAME:local-writer}") String instanceId) {
        this.repository = repository;
        this.finance = finance;
        this.financeProperties = financeProperties;
        this.appProperties = appProperties;
        this.clock = clock;
        this.financeZone = ZoneId.of(financeProperties.timeZone());
        this.instanceId = instanceId;
    }

    @Scheduled(initialDelayString = "${app.finance.recovery-initial-delay-ms:30000}",
            fixedDelayString = "${app.finance.recovery-delay-ms:300000}")
    public void recoverUnknownTransfers() {
        if (appProperties.instanceRole() != InstanceRole.WRITER) return;
        repository.unknownTransfers().forEach(this::recover);
    }

    private void recover(UnknownTransfer row) {
        long attemptId;
        try {
            attemptId = repository.prepareAttempt(row.transactionId(), "RESULT_QUERY", nextReference());
        } catch (RuntimeException exception) {
            return;
        }
        if (!repository.claimAttempt(attemptId, instanceId)) return;
        try {
            LocalDate start = row.dispatchedAt().atZoneSameInstant(financeZone).toLocalDate();
            LocalDate end = LocalDate.now(clock.withZone(financeZone));
            var matched = finance.inquireTransactions(financeProperties.escrowUserKey(),
                            financeProperties.escrowAccountNo(), start, end, "D", "DESC").stream()
                    .filter(item -> summary(row).equals(item.transactionSummary()))
                    .filter(item -> row.amount().equals(new BigInteger(item.transactionBalance())))
                    .findFirst();
            if (matched.isPresent()) {
                String externalId = matched.orElseThrow().transactionUniqueNo();
                repository.resolveUnknown(row.transactionId(), attemptId, externalId);
                repository.refreshSettlement(row.settlementId(), row.escrowId());
                repository.auditRecovery(row, "UNKNOWN_RESOLVED", externalId, "COMPLETED");
                return;
            }
            repository.finishAttempt(attemptId, "FAILED", null, "NOT_FOUND");
            repository.auditRecovery(row, "UNKNOWN_UNRESOLVED", "QUERY:" + attemptId, "UNKNOWN");
        } catch (RuntimeException exception) {
            repository.finishAttempt(attemptId, "FAILED", null, "Q1000");
            repository.auditRecovery(row, "UNKNOWN_UNRESOLVED", "QUERY:" + attemptId, "UNKNOWN");
        }
    }

    private String nextReference() {
        return REFERENCE_TIME.format(clock.instant().atZone(financeZone))
                + String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String summary(UnknownTransfer row) {
        return "KOMIT-" + row.type() + "-" + row.transactionId();
    }
}
