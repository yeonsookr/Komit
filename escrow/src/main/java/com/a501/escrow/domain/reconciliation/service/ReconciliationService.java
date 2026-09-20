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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {
    private final InternalApiSecurity security;
    private final ReconciliationRepository repository;
    private final FinanceClient finance;
    private final FinanceProperties properties;
    private final Clock clock;
    private final ZoneId financeZone;

    public ReconciliationService(InternalApiSecurity security, ReconciliationRepository repository,
            FinanceClient finance, FinanceProperties properties, Clock clock) {
        this.security = security;
        this.repository = repository;
        this.finance = finance;
        this.properties = properties;
        this.clock = clock;
        this.financeZone = ZoneId.of(properties.timeZone());
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ReconciliationDtos.Record inquire(ReconciliationDtos.Request request) {
        security.authenticate(request.header(), "inquireReconciliation", ApiRequirements.operatorRead());
        BigInteger ledger = repository.ledgerTotal();
        int count = repository.escrowCount();
        BigInteger bank;
        try {
            bank = finance.inquireAccountBalance(properties.escrowUserKey(), properties.escrowAccountNo());
        } catch (FinanceClientUnavailableException exception) {
            throw new EscrowApiException(HttpStatus.SERVICE_UNAVAILABLE, "Q1000",
                    "금융망 잔액을 확인할 수 없습니다.", request.header());
        }
        OffsetDateTime asOf = OffsetDateTime.now(clock.withZone(financeZone));
        ReconciliationDtos.HistoryItem current = repository.save(asOf, ledger, bank, count);
        List<ReconciliationDtos.HistoryItem> history = repository.history(request.requestedDays());
        return new ReconciliationDtos.Record(current.ledgerTotal(), current.bankBalance(),
                current.differenceAmount(), current.result(), count, history);
    }
}
