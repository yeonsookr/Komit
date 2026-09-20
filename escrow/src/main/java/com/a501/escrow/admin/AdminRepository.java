package com.a501.escrow.admin;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdminRepository {
    private final JdbcClient jdbc;

    public AdminRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        return jdbc.sql("""
                SELECT count(*) total,
                       count(*) FILTER (WHERE status='ACTIVE') active,
                       count(*) FILTER (WHERE hold_status='HELD') held,
                       count(*) FILTER (WHERE status IN ('COMPLETED','REFUNDED','CANCELLED')) closed,
                       COALESCE(sum(expected_amount),0) expected,
                       (SELECT count(*) FROM escrow_transactions WHERE status IN ('PROCESSING','UNKNOWN')) unresolved,
                       (SELECT count(*) FROM member_account_provisions WHERE provision_status='COMPLETED') accounts_completed,
                       (SELECT count(*) FROM member_account_provisions WHERE provision_status<>'COMPLETED') accounts_pending
                FROM escrows
                """).query((rs, row) -> new Overview(
                        rs.getLong("total"), rs.getLong("active"), rs.getLong("held"), rs.getLong("closed"),
                        rs.getBigDecimal("expected").toBigIntegerExact(), rs.getLong("unresolved"),
                        rs.getLong("accounts_completed"), rs.getLong("accounts_pending"))).single();
    }

    @Transactional(readOnly = true)
    public MoneySummary moneySummary() {
        return jdbc.sql("""
                SELECT COALESCE(sum(amount) FILTER (WHERE transaction_type='DEPOSIT' AND status='COMPLETED'),0) deposited,
                       COALESCE(sum(amount) FILTER (WHERE transaction_type='RELEASE' AND status='COMPLETED'),0) released,
                       COALESCE(sum(amount) FILTER (WHERE transaction_type='FEE' AND status='COMPLETED'),0) fees,
                       COALESCE(sum(amount) FILTER (WHERE transaction_type='REFUND' AND status='COMPLETED'),0) refunded
                FROM escrow_transactions
                """).query((rs, row) -> new MoneySummary(
                        money(rs, "deposited"), money(rs, "released"), money(rs, "fees"), money(rs, "refunded")))
                .single();
    }

    @Transactional(readOnly = true)
    public List<EscrowRow> recentEscrows() {
        return jdbc.sql("""
                SELECT e.id, sc.name service_name, e.external_contract_id, e.contract_type,
                       e.expected_amount, e.status, e.hold_status, e.created_at,
                       COALESCE(sum(CASE WHEN t.status='COMPLETED' AND t.transaction_type='DEPOSIT' THEN t.amount
                                         WHEN t.status='COMPLETED' AND t.transaction_type IN ('RELEASE','FEE','REFUND') THEN -t.amount
                                         ELSE 0 END),0) balance
                FROM escrows e
                JOIN service_clients sc ON sc.id=e.service_client_id
                LEFT JOIN escrow_transactions t ON t.escrow_id=e.id
                GROUP BY e.id,sc.name
                ORDER BY e.created_at DESC
                LIMIT 100
                """).query((rs, row) -> new EscrowRow(
                        rs.getLong("id"), rs.getString("service_name"), rs.getString("external_contract_id"),
                        rs.getString("contract_type"), money(rs, "expected_amount"), rs.getString("status"),
                        rs.getString("hold_status"), money(rs, "balance"),
                        rs.getObject("created_at", OffsetDateTime.class))).list();
    }

    @Transactional(readOnly = true)
    public List<ServiceClientRow> serviceClients() {
        return jdbc.sql("""
                SELECT name,status,one_won_auth_message,created_at,updated_at
                FROM service_clients ORDER BY name
                """).query((rs, row) -> new ServiceClientRow(
                        rs.getString("name"), rs.getString("status"), rs.getString("one_won_auth_message"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class))).list();
    }

    @Transactional(readOnly = true)
    public ReconciliationRow latestReconciliation() {
        return jdbc.sql("""
                SELECT as_of,ledger_total,bank_balance,difference_amount,result
                FROM reconciliation_snapshots ORDER BY as_of DESC LIMIT 1
                """).query((rs, row) -> new ReconciliationRow(
                        rs.getObject("as_of", OffsetDateTime.class), money(rs, "ledger_total"),
                        money(rs, "bank_balance"), money(rs, "difference_amount"), rs.getString("result")))
                .optional().orElse(null);
    }

    private static BigInteger money(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        return rs.getBigDecimal(column).toBigIntegerExact();
    }

    public record Overview(long total, long active, long held, long closed, BigInteger expected,
            long unresolved, long accountsCompleted, long accountsPending) {}
    public record MoneySummary(BigInteger deposited, BigInteger released, BigInteger fees, BigInteger refunded) {
        public BigInteger ledgerBalance() { return deposited.subtract(released).subtract(fees).subtract(refunded); }
    }
    public record EscrowRow(long id, String serviceName, String contractId, String contractType,
            BigInteger expectedAmount, String status, String holdStatus, BigInteger balance,
            OffsetDateTime createdAt) {}
    public record ServiceClientRow(String name, String status, String authMessage,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record ReconciliationRow(OffsetDateTime asOf, BigInteger ledgerTotal,
            BigInteger bankBalance, BigInteger difference, String result) {}
}
