package com.a501.escrow.domain.ledger.repository;

import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.a501.escrow.domain.escrow.repository.EscrowSettlementRepository.Totals;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복식부기 분개를 읽습니다. 분개는 V3 트리거가 거래 완료와 같은 DB 트랜잭션에서 남깁니다. (R-PAY-15)
 * 여기서 쓰지는 않습니다.
 */
@Repository
public class LedgerRepository {
    private static final String ESCROW_ID = "escrowId";

    private final JdbcClient jdbc;

    public LedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 계약의 예치금 반환 의무 잔액. 완료 거래 기준 잔액과 같습니다. */
    public BigInteger balance(long escrowId) {
        return jdbc.sql("""
                SELECT COALESCE(sum(CASE WHEN credit_account='ESCROW_LIABILITY' THEN amount ELSE -amount END),0)
                FROM ledger_postings
                WHERE escrow_id=:escrowId AND 'ESCROW_LIABILITY' IN (debit_account,credit_account)
                """).param(ESCROW_ID, escrowId).query(BigInteger.class).single();
    }

    /** 반환 의무를 줄인 분개를 유형별로 더합니다. 수수료는 반환 의무 쪽 줄만 셉니다. */
    public Totals totals(long escrowId) {
        return jdbc.sql("""
                SELECT COALESCE(sum(amount) FILTER (WHERE entry_type='RELEASE'),0) released,
                       COALESCE(sum(amount) FILTER (WHERE entry_type='FEE'),0) fee,
                       COALESCE(sum(amount) FILTER (WHERE entry_type='REFUND'),0) refunded
                FROM ledger_postings WHERE escrow_id=:escrowId AND debit_account='ESCROW_LIABILITY'
                """).param(ESCROW_ID, escrowId).query((rs, row) -> new Totals(
                        money(rs, "released"), money(rs, "fee"), money(rs, "refunded"))).single();
    }

    /** 보관계좌 현금 잔액. 대사가 금융망 잔액과 비교하는 원장 합계입니다. (R-PAY-13) */
    public BigInteger custodyTotal() {
        return jdbc.sql("""
                SELECT COALESCE(sum(CASE WHEN debit_account='CUSTODY_CASH' THEN amount ELSE -amount END),0)
                FROM ledger_postings WHERE 'CUSTODY_CASH' IN (debit_account,credit_account)
                """).query(BigInteger.class).single();
    }

    /** 반환 의무가 남은 계약 수. */
    public int fundedEscrowCount() {
        return jdbc.sql("""
                SELECT count(*) FROM (
                    SELECT escrow_id FROM ledger_postings
                    WHERE 'ESCROW_LIABILITY' IN (debit_account,credit_account)
                    GROUP BY escrow_id
                    HAVING sum(CASE WHEN credit_account='ESCROW_LIABILITY' THEN amount ELSE -amount END) > 0
                ) balances
                """).query(Integer.class).single();
    }

    /** 계정별 잔액과, 분개를 완료 거래와 맞대 본 결과. 운영 화면이 씁니다. */
    @Transactional(readOnly = true)
    public Check check() {
        return jdbc.sql("""
                WITH balances AS (
                    SELECT a.code,
                           COALESCE(sum(p.amount) FILTER (WHERE p.debit_account=a.code),0)
                         - COALESCE(sum(p.amount) FILTER (WHERE p.credit_account=a.code),0) debit_balance
                    FROM ledger_accounts a
                    LEFT JOIN ledger_postings p ON a.code IN (p.debit_account,p.credit_account)
                    GROUP BY a.code
                ), completed AS (
                    SELECT escrow_id, sum(CASE WHEN transaction_type='DEPOSIT' THEN amount ELSE -amount END) balance
                    FROM escrow_transactions WHERE status='COMPLETED' GROUP BY escrow_id
                ), liabilities AS (
                    SELECT escrow_id,
                           sum(CASE WHEN credit_account='ESCROW_LIABILITY' THEN amount ELSE -amount END) balance
                    FROM ledger_postings WHERE 'ESCROW_LIABILITY' IN (debit_account,credit_account)
                    GROUP BY escrow_id
                )
                SELECT (SELECT debit_balance FROM balances WHERE code='CUSTODY_CASH') custody,
                       (SELECT debit_balance FROM balances WHERE code='FEE_CASH') fee_cash,
                       (SELECT -debit_balance FROM balances WHERE code='ESCROW_LIABILITY') liability,
                       (SELECT -debit_balance FROM balances WHERE code='FEE_REVENUE') fee_revenue,
                       (SELECT COALESCE(sum(balance),0) FROM completed) transaction_balance,
                       (SELECT count(*) FROM completed c FULL JOIN liabilities l USING (escrow_id)
                        WHERE COALESCE(c.balance,0) <> COALESCE(l.balance,0)) mismatched_escrows,
                       (SELECT count(*) FROM escrow_transactions t WHERE t.status='COMPLETED'
                        AND NOT EXISTS (SELECT 1 FROM ledger_postings p WHERE p.transaction_id=t.id)) unposted,
                       (SELECT count(DISTINCT p.transaction_id) FROM ledger_postings p
                        JOIN escrow_transactions t ON t.id=p.transaction_id
                        WHERE t.status<>'COMPLETED' OR t.amount<>p.amount) diverged
                """).query((rs, row) -> new Check(
                        money(rs, "custody"), money(rs, "fee_cash"), money(rs, "liability"),
                        money(rs, "fee_revenue"), money(rs, "transaction_balance"),
                        rs.getLong("mismatched_escrows"), rs.getLong("unposted"), rs.getLong("diverged")))
                .single();
    }

    private static BigInteger money(ResultSet rs, String column) throws SQLException {
        return rs.getBigDecimal(column).toBigIntegerExact();
    }

    /**
     * @param custody            보관계좌 현금 (자산)
     * @param feeCash            수수료계좌 현금 (자산)
     * @param liability          예치금 반환 의무 (부채)
     * @param feeRevenue         수수료 수익
     * @param transactionBalance 완료 거래로 계산한 기존 원장 잔액
     * @param mismatchedEscrows  분개 잔액과 거래 잔액이 다른 계약 수
     * @param unposted           분개가 없는 완료 거래 수
     * @param diverged           분개 뒤 상태나 금액이 바뀐 거래 수
     */
    public record Check(BigInteger custody, BigInteger feeCash, BigInteger liability, BigInteger feeRevenue,
            BigInteger transactionBalance, long mismatchedEscrows, long unposted, long diverged) {

        /** 자산 = 부채 + 수익. 행마다 대차가 같으니 항상 성립해야 합니다. */
        public boolean balanced() {
            return custody.add(feeCash).equals(liability.add(feeRevenue));
        }

        public boolean consistent() {
            return balanced() && custody.equals(liability) && custody.equals(transactionBalance)
                    && feeCash.equals(feeRevenue) && mismatchedEscrows == 0 && unposted == 0 && diverged == 0;
        }
    }
}
