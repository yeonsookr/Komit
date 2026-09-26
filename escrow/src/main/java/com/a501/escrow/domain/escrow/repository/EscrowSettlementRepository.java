package com.a501.escrow.domain.escrow.repository;

import java.math.BigInteger;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.a501.escrow.config.LedgerProperties;
import com.a501.escrow.domain.ledger.repository.LedgerRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EscrowSettlementRepository {
    private static final String ESCROW_ID = "escrowId";
    private static final String REFERENCE = "reference";
    private static final String AMOUNT = "amount";
    private static final String STATUS = "status";
    private static final String TRANSACTION_ID = "transactionId";
    private static final String EXTERNAL_ID = "externalId";
    private final JdbcClient jdbc;
    private final LedgerRepository ledger;
    private final LedgerProperties ledgerProperties;

    public EscrowSettlementRepository(JdbcClient jdbc, LedgerRepository ledger, LedgerProperties ledgerProperties) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.ledgerProperties = ledgerProperties;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SettlementTransaction createRelease(long clientId, long escrowId, String reference,
            BigInteger approvedAmount, BigInteger netAmount, BigInteger feeAmount,
            String key, String fingerprint, String bankCode, String encryptedAccount, String name,
            String encryptedFeeAccount) {
        long settlementId = jdbc.sql("""
                INSERT INTO settlements(escrow_id,external_reference_id,approved_amount,status)
                VALUES(:escrowId,:reference,:amount,'PROCESSING') RETURNING id
                """).param(ESCROW_ID, escrowId).param(REFERENCE, reference).param(AMOUNT, approvedAmount)
                .query(Long.class).single();
        long releaseId = insertTransaction(new TransactionDraft(clientId, escrowId, settlementId, "RELEASE",
                netAmount, reference, key, fingerprint, bankCode, encryptedAccount, name));
        if (feeAmount.signum() > 0) {
            insertTransaction(new TransactionDraft(clientId, escrowId, settlementId, "FEE", feeAmount,
                    reference, key + ":FEE", fingerprint, "001", encryptedFeeAccount, "KOMIT"));
        }
        return findById(releaseId).orElseThrow();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SettlementTransaction createRefund(long clientId, long escrowId, long settlementId,
            String reference, BigInteger amount, String key, String fingerprint,
            String bankCode, String encryptedAccount, String name) {
        jdbc.sql("UPDATE settlements SET refund_amount=:amount,status='PROCESSING',updated_at=clock_timestamp() WHERE id=:id")
                .param(AMOUNT, amount).param("id", settlementId).update();
        long id = insertTransaction(new TransactionDraft(clientId, escrowId, settlementId, "REFUND", amount,
                reference, key, fingerprint, bankCode, encryptedAccount, name));
        return findById(id).orElseThrow();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SettlementTransaction createRefundSettlement(long clientId, long escrowId, String reference,
            BigInteger amount, String key, String fingerprint, String bankCode,
            String encryptedAccount, String name) {
        long settlementId = jdbc.sql("""
                INSERT INTO settlements(escrow_id,external_reference_id,refund_amount,status)
                VALUES(:escrowId,:reference,:amount,'PROCESSING') RETURNING id
                """).param(ESCROW_ID, escrowId).param(REFERENCE, reference).param(AMOUNT, amount)
                .query(Long.class).single();
        long id = insertTransaction(new TransactionDraft(clientId, escrowId, settlementId, "REFUND", amount,
                reference, key, fingerprint, bankCode, encryptedAccount, name));
        return findById(id).orElseThrow();
    }

    private long insertTransaction(TransactionDraft draft) {
        return jdbc.sql("""
                INSERT INTO escrow_transactions(service_client_id,escrow_id,settlement_id,transaction_type,
                    amount,status,external_reference_id,idempotency_key,request_fingerprint,
                    recipient_bank_code,recipient_account_encrypted,recipient_name)
                VALUES(:clientId,:escrowId,:settlementId,:type,:amount,'PENDING',:reference,:key,:fingerprint,
                    :bankCode,:account,:name) RETURNING id
                """).param("clientId", draft.clientId()).param(ESCROW_ID, draft.escrowId())
                .param("settlementId", draft.settlementId()).param("type", draft.type()).param(AMOUNT, draft.amount())
                .param(REFERENCE, draft.reference()).param("key", draft.key()).param("fingerprint", draft.fingerprint())
                .param("bankCode", draft.bankCode()).param("account", draft.encryptedAccount()).param("name", draft.name())
                .query(Long.class).single();
    }

    public Optional<SettlementTransaction> findByKey(long clientId, String type, String key) {
        return query("WHERE service_client_id=:clientId AND transaction_type=:type AND idempotency_key=:key",
                Map.of("clientId", clientId, "type", type, "key", key)).stream().findFirst();
    }

    public Optional<SettlementTransaction> findById(long id) {
        return query("WHERE id=:id", Map.of("id", id)).stream().findFirst();
    }

    public Optional<SettlementTransaction> findByReference(long escrowId, String type, String reference) {
        return query("WHERE escrow_id=:escrowId AND transaction_type=:type AND external_reference_id=:reference",
                Map.of(ESCROW_ID, escrowId, "type", type, REFERENCE, reference)).stream().findFirst();
    }

    public Optional<Long> settlementId(long escrowId, String reference) {
        return jdbc.sql("SELECT id FROM settlements WHERE escrow_id=:escrowId AND external_reference_id=:reference")
                .param(ESCROW_ID, escrowId).param(REFERENCE, reference).query(Long.class).optional();
    }

    private List<SettlementTransaction> query(String where, Map<String, ?> params) {
        return jdbc.sql("""
                SELECT id,settlement_id,escrow_id,transaction_type,amount,status,external_reference_id,
                       idempotency_key,request_fingerprint,recipient_bank_code,recipient_account_encrypted,
                       recipient_name,financial_transaction_id
                FROM escrow_transactions
                """ + where).params(params).query((rs, row) -> new SettlementTransaction(
                        rs.getLong("id"), rs.getLong("settlement_id"), rs.getLong("escrow_id"),
                        rs.getString("transaction_type"), rs.getBigDecimal(AMOUNT).toBigIntegerExact(),
                        rs.getString(STATUS), rs.getString("external_reference_id"),
                        rs.getString("idempotency_key"), rs.getString("request_fingerprint"),
                        rs.getString("recipient_bank_code"), rs.getString("recipient_account_encrypted"),
                        rs.getString("recipient_name"), rs.getString("financial_transaction_id"))).list();
    }

    /** 완료된 자금 이동만 반영한 잔액 (R-PAY-12). 어느 기록에서 읽을지는 app.ledger.read-source 를 따릅니다 (R-PAY-15). */
    public BigInteger balance(long escrowId) {
        if (ledgerProperties.readsLedger()) {
            return ledger.balance(escrowId);
        }
        return jdbc.sql("""
                SELECT COALESCE(sum(CASE WHEN transaction_type='DEPOSIT' THEN amount ELSE -amount END),0)
                FROM escrow_transactions WHERE escrow_id=:escrowId AND status='COMPLETED'
                """).param(ESCROW_ID, escrowId).query(BigInteger.class).single();
    }

    public Totals totals(long escrowId) {
        if (ledgerProperties.readsLedger()) {
            return ledger.totals(escrowId);
        }
        return jdbc.sql("""
                SELECT COALESCE(sum(amount) FILTER (WHERE transaction_type='RELEASE'),0) released,
                       COALESCE(sum(amount) FILTER (WHERE transaction_type='FEE'),0) fee,
                       COALESCE(sum(amount) FILTER (WHERE transaction_type='REFUND'),0) refunded
                FROM escrow_transactions WHERE escrow_id=:escrowId AND status='COMPLETED'
                """).param(ESCROW_ID, escrowId).query((rs, row) -> new Totals(
                        rs.getBigDecimal("released").toBigIntegerExact(),
                        rs.getBigDecimal("fee").toBigIntegerExact(),
                        rs.getBigDecimal("refunded").toBigIntegerExact())).single();
    }

    public BigInteger completedAmount(long settlementId, String type) {
        return jdbc.sql("SELECT COALESCE(sum(amount),0) FROM escrow_transactions WHERE settlement_id=:id AND transaction_type=:type AND status='COMPLETED'")
                .param("id", settlementId).param("type", type).query(BigInteger.class).single();
    }

    public Optional<FundingSource> fundingSource(long escrowId) {
        return jdbc.sql("""
                SELECT source_bank_code,source_account_encrypted
                FROM escrow_transactions WHERE escrow_id=:escrowId AND transaction_type='DEPOSIT' AND status='COMPLETED'
                """).param(ESCROW_ID, escrowId).query((rs, row) -> new FundingSource(
                        rs.getString("source_bank_code"), rs.getString("source_account_encrypted"))).optional();
    }

    public String settlementStatus(long settlementId) {
        return jdbc.sql("SELECT status FROM settlements WHERE id=:id").param("id", settlementId)
                .query(String.class).single();
    }

    public void processing(long transactionId) {
        jdbc.sql("UPDATE escrow_transactions SET status='PROCESSING',updated_at=clock_timestamp() WHERE id=:id AND status='PENDING'")
                .param("id", transactionId).update();
    }

    public void recipientName(long transactionId, String recipientName) {
        jdbc.sql("UPDATE escrow_transactions SET recipient_name=:name,updated_at=clock_timestamp() WHERE id=:id")
                .param("name", recipientName).param("id", transactionId).update();
    }

    public void complete(long transactionId, String externalId) {
        jdbc.sql("""
                UPDATE escrow_transactions SET status='COMPLETED',financial_transaction_id=:externalId,
                    completed_at=clock_timestamp(),updated_at=clock_timestamp() WHERE id=:id
                """).param(EXTERNAL_ID, externalId).param("id", transactionId).update();
    }

    public void unknown(long transactionId) {
        jdbc.sql("UPDATE escrow_transactions SET status='UNKNOWN',updated_at=clock_timestamp() WHERE id=:id")
                .param("id", transactionId).update();
    }

    public void failed(long transactionId, String code) {
        jdbc.sql("UPDATE escrow_transactions SET status='FAILED',failure_code=:code,updated_at=clock_timestamp() WHERE id=:id")
                .param("code", code).param("id", transactionId).update();
    }

    public void refreshSettlement(long settlementId, long escrowId) {
        BigInteger balance = balance(escrowId);
        Integer open = jdbc.sql("SELECT count(*) FROM escrow_transactions WHERE settlement_id=:id AND status IN ('PENDING','PROCESSING','UNKNOWN')")
                .param("id", settlementId).query(Integer.class).single();
        Integer unknown = jdbc.sql("SELECT count(*) FROM escrow_transactions WHERE settlement_id=:id AND status='UNKNOWN'")
                .param("id", settlementId).query(Integer.class).single();
        Integer failed = jdbc.sql("SELECT count(*) FROM escrow_transactions WHERE settlement_id=:id AND status='FAILED'")
                .param("id", settlementId).query(Integer.class).single();
        Integer completed = jdbc.sql("SELECT count(*) FROM escrow_transactions WHERE settlement_id=:id AND status='COMPLETED'")
                .param("id", settlementId).query(Integer.class).single();
        String status = settlementStatus(balance, open, unknown, failed, completed);
        jdbc.sql("""
                UPDATE settlements SET status=:status,closed_at=CASE WHEN :status='CLOSED' THEN clock_timestamp() ELSE NULL END,
                    updated_at=clock_timestamp() WHERE id=:id
                """).param(STATUS, status).param("id", settlementId).update();
        if ("CLOSED".equals(status)) {
            jdbc.sql("UPDATE escrows SET status=CASE WHEN EXISTS(SELECT 1 FROM escrow_transactions WHERE settlement_id=:id AND transaction_type='REFUND') THEN 'REFUNDED' ELSE 'COMPLETED' END,closed_at=clock_timestamp(),updated_at=clock_timestamp() WHERE id=:escrowId")
                    .param("id", settlementId).param(ESCROW_ID, escrowId).update();
        }
    }

    public Optional<Long> prepareAttempt(long transactionId, String step, String reference) {
        return jdbc.sql("""
                INSERT INTO financial_operation_attempts(transaction_id,step,attempt_no,request_reference)
                SELECT :transactionId,:step,COALESCE(max(attempt_no),0)+1,:reference
                FROM financial_operation_attempts WHERE transaction_id=:transactionId AND step=:step
                ON CONFLICT DO NOTHING
                RETURNING id
                """).param(TRANSACTION_ID, transactionId).param("step", step).param(REFERENCE, reference)
                .query(Long.class).optional();
    }

    public Optional<Long> prepareRecoveryAttempt(long transactionId, String reference) {
        return jdbc.sql("""
                INSERT INTO financial_operation_attempts(transaction_id,step,attempt_no,request_reference)
                SELECT :transactionId,'RESULT_QUERY',COALESCE(max(attempt_no),0)+1,:reference
                FROM financial_operation_attempts
                WHERE transaction_id=:transactionId AND step='RESULT_QUERY'
                ON CONFLICT DO NOTHING
                RETURNING id
                """).param(TRANSACTION_ID, transactionId).param(REFERENCE, reference)
                .query(Long.class).optional();
    }

    public boolean claimAttempt(long id, String instanceId) {
        return jdbc.sql("""
                UPDATE financial_operation_attempts SET status='DISPATCHING',dispatched_by=:instance,
                    dispatched_at=clock_timestamp() WHERE id=:id AND status='REQUESTED'
                """).param("instance", instanceId).param("id", id).update() == 1;
    }

    public String attemptReference(long id) {
        return jdbc.sql("SELECT request_reference FROM financial_operation_attempts WHERE id=:id")
                .param("id", id).query(String.class).single();
    }

    public void finishAttempt(long id, String status, String externalId, String code) {
        jdbc.sql("""
                UPDATE financial_operation_attempts SET status=:status,external_transaction_id=:externalId,
                    response_code=:code,
                    responded_at=CASE WHEN :status='UNKNOWN' THEN NULL ELSE clock_timestamp() END
                WHERE id=:id
                """).param(STATUS, status).param(EXTERNAL_ID, externalId, Types.VARCHAR)
                .param("code", code, Types.VARCHAR).param("id", id).update();
    }

    public List<UnknownTransfer> unknownTransfers() {
        return jdbc.sql("""
                SELECT t.id,t.settlement_id,t.escrow_id,t.transaction_type,t.amount,
                       min(a.dispatched_at) dispatched_at
                FROM escrow_transactions t
                JOIN financial_operation_attempts a ON a.transaction_id=t.id
                WHERE t.status='UNKNOWN'
                  AND t.transaction_type IN ('RELEASE','FEE','REFUND')
                  AND a.status='UNKNOWN'
                  AND a.step IN ('PAYOUT_TRANSFER','FEE_TRANSFER','REFUND_TRANSFER')
                GROUP BY t.id,t.settlement_id,t.escrow_id,t.transaction_type,t.amount
                ORDER BY t.id
                """).query((rs, row) -> new UnknownTransfer(rs.getLong("id"),
                        rs.getLong("settlement_id"), rs.getLong("escrow_id"),
                        rs.getString("transaction_type"), rs.getBigDecimal(AMOUNT).toBigIntegerExact(),
                        rs.getObject("dispatched_at", OffsetDateTime.class))).list();
    }

    @Transactional
    public int markStaleDispatchingUnknown(OffsetDateTime cutoff) {
        int staleQueries = jdbc.sql("""
                UPDATE financial_operation_attempts
                SET status='FAILED',response_code='QUERY_TIMEOUT',responded_at=clock_timestamp()
                WHERE status='DISPATCHING'
                  AND dispatched_at < :cutoff
                  AND step='RESULT_QUERY'
                """).param("cutoff", cutoff).update();
        List<Long> transactionIds = jdbc.sql("""
                UPDATE financial_operation_attempts
                SET status='UNKNOWN',response_code='DISPATCH_TIMEOUT'
                WHERE status='DISPATCHING'
                  AND dispatched_at < :cutoff
                  AND step IN ('PAYOUT_TRANSFER','FEE_TRANSFER','REFUND_TRANSFER')
                RETURNING transaction_id
                """).param("cutoff", cutoff).query(Long.class).list();
        if (transactionIds.isEmpty()) return staleQueries;
        jdbc.sql("""
                UPDATE escrow_transactions
                SET status='UNKNOWN',updated_at=clock_timestamp()
                WHERE id IN (:ids) AND status='PROCESSING'
                """).param("ids", transactionIds).update();
        return staleQueries + transactionIds.size();
    }

    public void resolveUnknown(long transactionId, long queryAttemptId, String externalId) {
        jdbc.sql("""
                UPDATE financial_operation_attempts SET status='SUCCEEDED',external_transaction_id=:externalId,
                    response_code='RECOVERED' WHERE transaction_id=:transactionId AND status='UNKNOWN'
                """).param(EXTERNAL_ID, externalId).param(TRANSACTION_ID, transactionId).update();
        finishAttempt(queryAttemptId, "SUCCEEDED", externalId, "H0000");
        complete(transactionId, externalId);
    }

    public void auditRecovery(UnknownTransfer row, String action, String evidence, String result) {
        jdbc.sql("""
                INSERT INTO operation_audit_events(escrow_id,transaction_id,actor_type,actor_id,action,reason,
                    evidence_reference,previous_status,resulting_status)
                VALUES(:escrowId,:transactionId,'SYSTEM','financial-recovery',:action,
                    'R-PAY-12 financial call result inquiry',:evidence,'UNKNOWN',:result)
                """).param(ESCROW_ID, row.escrowId()).param(TRANSACTION_ID, row.transactionId())
                .param("action", action).param("evidence", evidence).param("result", result).update();
    }

    public record FundingSource(String bankCode, String accountEncrypted) {
    }

    public record Totals(BigInteger released, BigInteger fee, BigInteger refunded) {
    }

    private static String settlementStatus(BigInteger balance, int open, int unknown, int failed, int completed) {
        if (unknown > 0) return "UNKNOWN";
        if (open > 0) return "PROCESSING";
        if (failed > 0) return completed > 0 ? "PROCESSING" : "FAILED";
        return balance.signum() == 0 ? "CLOSED" : "OPEN";
    }

    private record TransactionDraft(long clientId, long escrowId, long settlementId, String type,
            BigInteger amount, String reference, String key, String fingerprint,
            String bankCode, String encryptedAccount, String name) {
    }

    public record UnknownTransfer(long transactionId, long settlementId, long escrowId,
            String type, BigInteger amount, OffsetDateTime dispatchedAt) {
    }
}
