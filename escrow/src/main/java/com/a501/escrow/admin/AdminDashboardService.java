package com.a501.escrow.admin;

import java.math.BigInteger;

import com.a501.escrow.config.FinanceProperties;
import com.a501.escrow.domain.ledger.repository.LedgerRepository;
import com.a501.escrow.integration.finance.FinanceClient;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {
    private final AdminRepository repository;
    private final LedgerRepository ledger;
    private final FinanceClient finance;
    private final FinanceProperties financeProperties;

    public AdminDashboardService(AdminRepository repository, LedgerRepository ledger, FinanceClient finance,
            FinanceProperties financeProperties) {
        this.repository = repository;
        this.ledger = ledger;
        this.finance = finance;
        this.financeProperties = financeProperties;
    }

    public Dashboard dashboard() {
        BigInteger escrowBalance = null;
        BigInteger feeBalance = null;
        String financeError = null;
        try {
            escrowBalance = finance.inquireAccountBalance(
                    financeProperties.escrowUserKey(), financeProperties.escrowAccountNo());
            feeBalance = finance.inquireAccountBalance(
                    financeProperties.escrowUserKey(), financeProperties.feeAccountNo());
        } catch (RuntimeException exception) {
            financeError = "금융망 계좌 잔액을 조회할 수 없습니다.";
        }
        return new Dashboard(repository.overview(), repository.moneySummary(), ledger.check(), repository.recentEscrows(),
                repository.serviceClients(), repository.latestReconciliation(),
                mask(financeProperties.escrowAccountNo()), mask(financeProperties.feeAccountNo()),
                escrowBalance, feeBalance, financeError);
    }

    private static String mask(String accountNo) {
        if (accountNo == null || accountNo.length() < 8) return "-";
        return accountNo.substring(0, 4) + "-****-****-" + accountNo.substring(accountNo.length() - 4);
    }

    public record Dashboard(AdminRepository.Overview overview, AdminRepository.MoneySummary money,
            LedgerRepository.Check ledger,
            java.util.List<AdminRepository.EscrowRow> escrows,
            java.util.List<AdminRepository.ServiceClientRow> clients,
            AdminRepository.ReconciliationRow reconciliation, String escrowAccount,
            String feeAccount, BigInteger escrowBankBalance, BigInteger feeBankBalance,
            String financeError) {}
}
