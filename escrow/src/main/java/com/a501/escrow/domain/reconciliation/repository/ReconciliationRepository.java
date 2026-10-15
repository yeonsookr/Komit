package com.a501.escrow.domain.reconciliation.repository;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.List;

import com.a501.escrow.config.LedgerProperties;
import com.a501.escrow.domain.ledger.repository.LedgerRepository;
import com.a501.escrow.domain.reconciliation.dto.ReconciliationDtos;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReconciliationRepository {
    private static final String RESULT = "result";

    private final JdbcClient jdbc;
    private final LedgerRepository ledger;
    private final LedgerProperties ledgerProperties;

    public ReconciliationRepository(JdbcClient jdbc, LedgerRepository ledger, LedgerProperties ledgerProperties) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.ledgerProperties = ledgerProperties;
    }

    /** 보관계좌에 있어야 할 금액 (R-PAY-13). 어느 기록에서 읽을지는 app.ledger.read-source 를 따릅니다 (R-PAY-15). */
    public BigInteger ledgerTotal() {
        if (ledgerProperties.readsLedger()) {
            return ledger.custodyTotal();
        }
        return jdbc.sql("""
                SELECT COALESCE(sum(CASE WHEN transaction_type='DEPOSIT' THEN amount ELSE -amount END),0)
                FROM escrow_transactions WHERE status='COMPLETED'
                """).query(BigInteger.class).single();
    }

    public int escrowCount() {
        if (ledgerProperties.readsLedger()) {
            return ledger.fundedEscrowCount();
        }
        return jdbc.sql("""
                SELECT count(*) FROM (
                    SELECT escrow_id
                    FROM escrow_transactions
                    WHERE status='COMPLETED'
                    GROUP BY escrow_id
                    HAVING sum(CASE WHEN transaction_type='DEPOSIT' THEN amount ELSE -amount END) > 0
                ) balances
                """).query(Integer.class).single();
    }

    public ReconciliationDtos.HistoryItem save(OffsetDateTime asOf, BigInteger ledger,
            BigInteger bank, int escrowCount) {
        BigInteger difference = bank.subtract(ledger);
        String result = difference.signum() == 0 ? "MATCHED" : "BANK_MISMATCH";
        return jdbc.sql("""
                INSERT INTO reconciliation_snapshots(as_of,ledger_total,bank_balance,difference_amount,result,escrow_count)
                VALUES(:asOf,:ledger,:bank,:difference,:result,:count)
                RETURNING as_of,ledger_total,bank_balance,difference_amount,result
                """).param("asOf", asOf).param("ledger", ledger).param("bank", bank)
                .param("difference", difference).param(RESULT, result).param("count", escrowCount)
                .query((rs, row) -> item(rs.getObject("as_of", OffsetDateTime.class),
                        rs.getBigDecimal("ledger_total").toBigIntegerExact(),
                        rs.getBigDecimal("bank_balance").toBigIntegerExact(),
                        rs.getBigDecimal("difference_amount").toBigIntegerExact(), rs.getString(RESULT)))
                .single();
    }

    public List<ReconciliationDtos.HistoryItem> history(int days) {
        return jdbc.sql("""
                SELECT DISTINCT ON ((as_of AT TIME ZONE 'Asia/Seoul')::date)
                       as_of,ledger_total,bank_balance,difference_amount,result
                FROM reconciliation_snapshots
                WHERE as_of >= clock_timestamp() - (:days * interval '1 day')
                ORDER BY (as_of AT TIME ZONE 'Asia/Seoul')::date DESC, as_of DESC
                """).param("days", days).query((rs, row) -> item(
                        rs.getObject("as_of", OffsetDateTime.class),
                        rs.getBigDecimal("ledger_total").toBigIntegerExact(),
                        rs.getBigDecimal("bank_balance").toBigIntegerExact(),
                        rs.getBigDecimal("difference_amount").toBigIntegerExact(), rs.getString(RESULT))).list();
    }

    private static ReconciliationDtos.HistoryItem item(OffsetDateTime asOf, BigInteger ledger,
            BigInteger bank, BigInteger difference, String result) {
        return new ReconciliationDtos.HistoryItem(asOf, ledger, bank, difference, result);
    }
}
