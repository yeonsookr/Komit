package com.a501.escrow.domain.reconciliation.service;

import java.math.BigInteger;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.a501.escrow.common.exception.EscrowApiException;
import com.a501.escrow.common.security.ApiRequirements;
import com.a501.escrow.common.security.InternalApiSecurity;
import com.a501.escrow.config.FinanceProperties;
import com.a501.escrow.domain.reconciliation.dto.ReconciliationDtos;
import com.a501.escrow.domain.reconciliation.repository.ReconciliationRepository;
import com.a501.escrow.integration.finance.FinanceClient;
import com.a501.escrow.integration.finance.FinanceClientUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReconciliationService {
    private final InternalApiSecurity security;
    private final ReconciliationRepository repository;
    private final FinanceClient finance;
    private final FinanceProperties properties;
    private final Clock clock;
    private final ZoneId financeZone;
    private final TransactionTemplate snapshotTransaction;
    private final TransactionTemplate writeTransaction;

    public ReconciliationService(InternalApiSecurity security, ReconciliationRepository repository,
            FinanceClient finance, FinanceProperties properties, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.security = security;
        this.repository = repository;
        this.finance = finance;
        this.properties = properties;
        this.clock = clock;
        this.financeZone = ZoneId.of(properties.timeZone());
        this.snapshotTransaction = new TransactionTemplate(transactionManager);
        this.snapshotTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        // 대사는 최신 원장이 필요한 금융 판단이므로 Primary에서 읽는다.
        this.snapshotTransaction.setReadOnly(false);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    public ReconciliationDtos.Record inquire(ReconciliationDtos.Request request) {
        security.authenticate(request.header(), "inquireReconciliation", ApiRequirements.operatorRead());
        LedgerSnapshot snapshot = snapshotTransaction.execute(status ->
                new LedgerSnapshot(repository.ledgerTotal(), repository.escrowCount()));
        if (snapshot == null) {
            throw new IllegalStateException("원장 스냅샷을 생성할 수 없습니다.");
        }
        BigInteger bank;
        try {
            bank = finance.inquireAccountBalance(properties.escrowUserKey(), properties.escrowAccountNo());
        } catch (FinanceClientUnavailableException exception) {
            throw new EscrowApiException(HttpStatus.SERVICE_UNAVAILABLE, "Q1000",
                    "금융망 잔액을 확인할 수 없습니다.", request.header());
        }
        OffsetDateTime asOf = OffsetDateTime.now(clock.withZone(financeZone));
        StoredSnapshot stored = writeTransaction.execute(status -> {
            ReconciliationDtos.HistoryItem current = repository.save(
                    asOf, snapshot.ledgerTotal(), bank, snapshot.escrowCount());
            return new StoredSnapshot(current, repository.history(request.requestedDays()));
        });
        if (stored == null) {
            throw new IllegalStateException("대사 결과를 저장할 수 없습니다.");
        }
        ReconciliationDtos.HistoryItem current = stored.current();
        List<ReconciliationDtos.HistoryItem> history = stored.history();
        return new ReconciliationDtos.Record(current.ledgerTotal(), current.bankBalance(),
                current.differenceAmount(), current.result(), snapshot.escrowCount(), history);
    }

    private record LedgerSnapshot(BigInteger ledgerTotal, int escrowCount) {
    }

    private record StoredSnapshot(ReconciliationDtos.HistoryItem current,
            List<ReconciliationDtos.HistoryItem> history) {
    }
}
