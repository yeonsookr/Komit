package com.a501.escrow.domain.escrow.repository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.a501.escrow.domain.escrow.dto.EscrowDtos;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
// SQL column/parameter names intentionally mirror the database contract; extracting them obscures each statement.
@SuppressWarnings({"java:S107", "java:S1192", "java:S6203"})
public class EscrowDepositRepository {
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    public EscrowDepositRepository(JdbcClient jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public Optional<DepositEscrow> findByIdempotencyKey(long clientId, String key) {
        return query("t.service_client_id=:clientId AND t.transaction_type='DEPOSIT' AND t.idempotency_key=:value",
                clientId, key);
    }

    public Optional<DepositEscrow> findByContractId(long clientId, String contractId) {
        return query("e.service_client_id=:clientId AND e.external_contract_id=:value", clientId, contractId);
    }

    public Optional<DepositEscrow> findById(long clientId, long escrowId) {
        return query("e.service_client_id=:clientId AND e.id=:escrowId", Map.of(
                "clientId", clientId, "escrowId", escrowId));
    }

    private Optional<DepositEscrow> query(String predicate, long clientId, String value) {
        return query(predicate, Map.of("clientId", clientId, "value", value));
    }

    private Optional<DepositEscrow> query(String predicate, Map<String, ?> parameters) {
        return jdbc.sql("""
                SELECT e.id escrow_id, t.id transaction_id, e.service_client_id,
                  e.external_contract_id, e.contract_type, e.expected_amount, e.status escrow_status,
                  t.status transaction_status, t.idempotency_key, t.request_fingerprint, t.deposit_step,
                  t.depositor_name_encrypted, t.virtual_account_id, t.virtual_account_no,
                  t.deposit_verification_status, t.verified_deposit_amount, t.depositor_name_matched,
                  t.source_bank_code, t.source_account_encrypted
                FROM escrows e JOIN escrow_transactions t ON t.escrow_id=e.id
                  AND t.service_client_id=e.service_client_id AND t.transaction_type='DEPOSIT'
                WHERE """ + " " + predicate).params(parameters).query((rs, row) -> new DepositEscrow(
                        rs.getLong("escrow_id"), rs.getLong("transaction_id"),
                        rs.getLong("service_client_id"), rs.getString("external_contract_id"),
                        rs.getString("contract_type"), integer(rs.getBigDecimal("expected_amount")),
                        rs.getString("escrow_status"), rs.getString("transaction_status"),
                        rs.getString("idempotency_key"), rs.getString("request_fingerprint"),
                        rs.getString("deposit_step"), rs.getString("depositor_name_encrypted"),
                        rs.getString("virtual_account_id"), rs.getString("virtual_account_no"),
                        rs.getString("deposit_verification_status"), integer(rs.getBigDecimal("verified_deposit_amount")),
                        rs.getObject("depositor_name_matched", Boolean.class), rs.getString("source_bank_code"),
                        rs.getString("source_account_encrypted"))).optional();
    }

    public void create(long clientId, long provisionId, String contractId, String contractType,
            BigInteger amount, String idempotencyKey, String fingerprint, String encryptedDepositor,
            String bankCode, String encryptedAccount) {
        try {
            transactions.executeWithoutResult(status -> {
                long escrowId = jdbc.sql("""
                        INSERT INTO escrows(service_client_id,payer_provision_id,external_contract_id,
                          contract_type,expected_amount,fee_rate_bps,status)
                        VALUES (:clientId,:provisionId,:contractId,:contractType,:amount,100,'CREATED')
                        RETURNING id
                        """).param("clientId", clientId).param("provisionId", provisionId)
                        .param("contractId", contractId).param("contractType", contractType)
                        .param("amount", new BigDecimal(amount)).query(Long.class).single();
                jdbc.sql("""
                        INSERT INTO escrow_transactions(service_client_id,escrow_id,transaction_type,amount,
                          status,external_reference_id,idempotency_key,request_fingerprint,deposit_step,
                          depositor_name_encrypted,deposit_verification_status,source_bank_code,source_account_encrypted)
                        VALUES (:clientId,:escrowId,'DEPOSIT',:amount,'PENDING','INITIAL',:key,:fingerprint,
                          'VA_CREATING',:depositor,'PENDING',:bankCode,:account)
                        """).param("clientId", clientId).param("escrowId", escrowId)
                        .param("amount", new BigDecimal(amount)).param("key", idempotencyKey)
                        .param("fingerprint", fingerprint).param("depositor", encryptedDepositor)
                        .param("bankCode", bankCode).param("account", encryptedAccount).update();
            });
        } catch (DuplicateKeyException exception) {
            // Only expected business-key races are replayable; unrelated constraint failures must surface.
            if (findByIdempotencyKey(clientId, idempotencyKey).isEmpty()
                    && findByContractId(clientId, contractId).isEmpty()) {
                throw exception;
            }
        }
    }

    public long prepareAttempt(long transactionId, String step, String reference) {
        jdbc.sql("""
                INSERT INTO financial_operation_attempts(transaction_id,step,attempt_no,request_reference)
                VALUES (:transactionId,:step,1,:reference)
                ON CONFLICT DO NOTHING
                """).param("transactionId", transactionId).param("step", step)
                .param("reference", reference).update();
        return jdbc.sql("""
                SELECT id FROM financial_operation_attempts
                WHERE transaction_id=:transactionId AND step=:step ORDER BY attempt_no DESC LIMIT 1
                """).param("transactionId", transactionId).param("step", step).query(Long.class).single();
    }

    public String attemptReference(long attemptId) {
        return jdbc.sql("SELECT request_reference FROM financial_operation_attempts WHERE id=:id")
                .param("id", attemptId).query(String.class).single();
    }

    public boolean claimAttempt(long attemptId, String instanceId) {
        return jdbc.sql("""
                UPDATE financial_operation_attempts SET status='DISPATCHING', dispatched_by=:instanceId,
                  dispatched_at=clock_timestamp() WHERE id=:id AND status='REQUESTED'
                """).param("instanceId", instanceId).param("id", attemptId).update() == 1;
    }

    public void attemptSucceeded(long attemptId, String externalId) {
        jdbc.sql("""
                UPDATE financial_operation_attempts SET status='SUCCEEDED', external_transaction_id=:externalId,
                  response_code='H0000', responded_at=clock_timestamp() WHERE id=:id AND status='DISPATCHING'
                """).param("externalId", externalId).param("id", attemptId).update();
    }

    public void attemptFailed(long attemptId, String code) {
        jdbc.sql("""
                UPDATE financial_operation_attempts SET status='FAILED', response_code=:code,
                  responded_at=clock_timestamp() WHERE id=:id AND status='DISPATCHING'
                """).param("code", code).param("id", attemptId).update();
    }

    public void attemptUnknown(long attemptId) {
        jdbc.sql("""
                UPDATE financial_operation_attempts SET status='UNKNOWN', responded_at=clock_timestamp()
                WHERE id=:id AND status='DISPATCHING'
                """).param("id", attemptId).update();
    }

    public void finishDispatching(long transactionId, String status, String code) {
        jdbc.sql("""
                UPDATE financial_operation_attempts SET status=:status, response_code=:code,
                  responded_at=clock_timestamp()
                WHERE transaction_id=:transactionId AND status='DISPATCHING'
                """).param("status", status).param("code", code)
                .param("transactionId", transactionId).update();
    }

    public void virtualAccountCreated(long transactionId, String virtualAccountId, String virtualAccountNo) {
        jdbc.sql("""
                UPDATE escrow_transactions SET deposit_step='VA_CREATED', virtual_account_id=:virtualAccountId,
                  virtual_account_no=:virtualAccountNo, status='PROCESSING', updated_at=clock_timestamp()
                WHERE id=:id AND deposit_step='VA_CREATING'
                """).param("virtualAccountId", virtualAccountId).param("virtualAccountNo", virtualAccountNo)
                .param("id", transactionId).update();
    }

    public void transferStarted(long transactionId) {
        jdbc.sql("UPDATE escrow_transactions SET deposit_step='TRANSFER_SENDING', updated_at=clock_timestamp() "
                + "WHERE id=:id AND deposit_step='VA_CREATED'").param("id", transactionId).update();
    }

    public void transferred(long transactionId, String externalTransactionId) {
        jdbc.sql("""
                UPDATE escrow_transactions SET deposit_step='TRANSFERRED', financial_transaction_id=:externalId,
                  updated_at=clock_timestamp() WHERE id=:id AND deposit_step='TRANSFER_SENDING'
                """).param("externalId", externalTransactionId).param("id", transactionId).update();
    }

    public void verified(long transactionId, BigInteger verifiedAmount, boolean nameMatched, boolean matched) {
        jdbc.sql("""
                UPDATE escrow_transactions SET deposit_step='VERIFIED', verified_deposit_amount=:amount,
                  depositor_name_matched=:nameMatched, deposit_verification_status=:verification,
                  updated_at=clock_timestamp() WHERE id=:id AND deposit_step='TRANSFERRED'
                """).param("amount", new BigDecimal(verifiedAmount)).param("nameMatched", nameMatched)
                .param("verification", matched ? "MATCHED" : "MISMATCHED")
                .param("id", transactionId).update();
    }

    public void settled(long escrowId, long transactionId, BigInteger settledAmount) {
        transactions.executeWithoutResult(status -> {
            jdbc.sql("""
                    UPDATE escrow_transactions SET deposit_step='SETTLED', status='COMPLETED',
                      verified_deposit_amount=:amount, completed_at=clock_timestamp(), updated_at=clock_timestamp()
                    WHERE id=:id AND deposit_step='VERIFIED' AND deposit_verification_status='MATCHED'
                    """).param("amount", new BigDecimal(settledAmount)).param("id", transactionId).update();
            jdbc.sql("UPDATE escrows SET status='ACTIVE', updated_at=clock_timestamp() "
                    + "WHERE id=:id AND status IN ('CREATED','DEPOSIT_PENDING')")
                    .param("id", escrowId).update();
        });
    }

    public void recoveredTransfer(long transactionId, BigInteger amount, boolean nameMatched) {
        jdbc.sql("""
                UPDATE escrow_transactions SET deposit_step='TRANSFERRED', status='PROCESSING',
                  verified_deposit_amount=:amount, depositor_name_matched=:nameMatched,
                  deposit_verification_status='PENDING', updated_at=clock_timestamp()
                WHERE id=:id AND status='UNKNOWN'
                """).param("amount", new BigDecimal(amount)).param("nameMatched", nameMatched)
                .param("id", transactionId).update();
    }

    public void recoveredSettlement(long escrowId, long transactionId, BigInteger amount) {
        transactions.executeWithoutResult(status -> {
            jdbc.sql("""
                    UPDATE escrow_transactions SET deposit_step='SETTLED',status='COMPLETED',
                      deposit_verification_status='MATCHED',verified_deposit_amount=:amount,
                      depositor_name_matched=true,completed_at=clock_timestamp(),updated_at=clock_timestamp()
                    WHERE id=:id AND status='UNKNOWN'
                    """).param("amount", new BigDecimal(amount)).param("id", transactionId).update();
            jdbc.sql("UPDATE escrows SET status='ACTIVE',updated_at=clock_timestamp() WHERE id=:id AND status='DEPOSIT_PENDING'")
                    .param("id", escrowId).update();
        });
    }

    public void closed(long transactionId) {
        jdbc.sql("""
                UPDATE escrow_transactions SET deposit_step='VA_CLOSED', virtual_account_closed_at=clock_timestamp(),
                  updated_at=clock_timestamp() WHERE id=:id AND deposit_step='SETTLED'
                """).param("id", transactionId).update();
    }

    public void pending(long escrowId) {
        jdbc.sql("UPDATE escrows SET status='DEPOSIT_PENDING', updated_at=clock_timestamp() "
                + "WHERE id=:id AND status='CREATED'").param("id", escrowId).update();
    }

    public void unknown(long escrowId, long transactionId) {
        transactions.executeWithoutResult(status -> {
            jdbc.sql("UPDATE escrow_transactions SET status='UNKNOWN', deposit_verification_status='UNKNOWN', "
                    + "updated_at=clock_timestamp() WHERE id=:id AND status<>'COMPLETED'")
                    .param("id", transactionId).update();
            pending(escrowId);
        });
    }

    public void failed(long escrowId, long transactionId, String code) {
        transactions.executeWithoutResult(status -> {
            jdbc.sql("UPDATE escrow_transactions SET status='FAILED', failure_code=:code, updated_at=clock_timestamp() "
                    + "WHERE id=:id AND status<>'COMPLETED'").param("code", code).param("id", transactionId).update();
            pending(escrowId);
        });
    }

    public boolean cancel(long clientId, long escrowId, String key, String fingerprint) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            DepositEscrow escrow = jdbc.sql("""
                    SELECT e.id escrow_id, t.id transaction_id, e.service_client_id, e.external_contract_id,
                      e.contract_type,e.expected_amount,e.status escrow_status,t.status transaction_status,
                      t.idempotency_key,t.request_fingerprint,t.deposit_step,t.depositor_name_encrypted,
                      t.virtual_account_id,t.virtual_account_no,t.deposit_verification_status,
                      t.verified_deposit_amount,t.depositor_name_matched,t.source_bank_code,t.source_account_encrypted
                    FROM escrows e JOIN escrow_transactions t ON t.escrow_id=e.id AND t.transaction_type='DEPOSIT'
                    WHERE e.id=:escrowId AND e.service_client_id=:clientId FOR UPDATE OF e,t
                    """).param("escrowId", escrowId).param("clientId", clientId)
                    .query((rs, row) -> map(rs)).optional().orElse(null);
            if (escrow == null || !("FAILED".equals(escrow.transactionStatus())
                    || ("PENDING".equals(escrow.transactionStatus()) && "VA_CREATING".equals(escrow.depositStep())))) {
                return false;
            }
            jdbc.sql("UPDATE escrows SET status='CANCELLED',cancelled_at=clock_timestamp(),closed_at=clock_timestamp(),updated_at=clock_timestamp() WHERE id=:id")
                    .param("id", escrowId).update();
            jdbc.sql("""
                    INSERT INTO escrow_state_commands(service_client_id,escrow_id,api_name,idempotency_key,
                      request_fingerprint,result_state) VALUES (:clientId,:escrowId,'cancelEscrow',:key,:fingerprint,'CANCELLED')
                    ON CONFLICT DO NOTHING
                    """).param("clientId", clientId).param("escrowId", escrowId)
                    .param("key", key).param("fingerprint", fingerprint).update();
            return true;
        }));
    }

    public Optional<CancelCommand> findCancelCommand(long clientId, String key) {
        return jdbc.sql("""
                SELECT escrow_id,request_fingerprint FROM escrow_state_commands
                WHERE service_client_id=:clientId AND api_name='cancelEscrow'
                  AND idempotency_key=:key
                """).param("clientId", clientId).param("key", key)
                .query((rs, row) -> new CancelCommand(
                        rs.getLong("escrow_id"), rs.getString("request_fingerprint"))).optional();
    }

    public List<EscrowDtos.TransactionItem> transactions(long escrowId) {
        return jdbc.sql("""
                SELECT id,transaction_type,amount,status,external_reference_id FROM escrow_transactions
                WHERE escrow_id=:escrowId ORDER BY id
                """).param("escrowId", escrowId).query((rs, row) -> new EscrowDtos.TransactionItem(
                        rs.getLong("id"), rs.getString("transaction_type"),
                        integer(rs.getBigDecimal("amount")), rs.getString("status"),
                        rs.getString("external_reference_id"))).list();
    }

    public List<DepositEscrow> list(long clientId, List<String> contractIds, String escrowStatus) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.id escrow_id, t.id transaction_id, e.service_client_id, e.external_contract_id,
                  e.contract_type,e.expected_amount,e.status escrow_status,t.status transaction_status,
                  t.idempotency_key,t.request_fingerprint,t.deposit_step,t.depositor_name_encrypted,
                  t.virtual_account_id,t.virtual_account_no,t.deposit_verification_status,
                  t.verified_deposit_amount,t.depositor_name_matched,t.source_bank_code,t.source_account_encrypted
                FROM escrows e JOIN escrow_transactions t ON t.escrow_id=e.id AND t.transaction_type='DEPOSIT'
                WHERE e.service_client_id=:clientId
                """);
        java.util.HashMap<String, Object> params = new java.util.HashMap<>();
        params.put("clientId", clientId);
        if (contractIds != null) {
            if (contractIds.isEmpty()) return List.of();
            sql.append(" AND e.external_contract_id IN (:contractIds)");
            params.put("contractIds", contractIds);
        }
        if (escrowStatus != null) {
            sql.append(" AND e.status=:status");
            params.put("status", escrowStatus);
        }
        sql.append(" ORDER BY e.id");
        return jdbc.sql(sql.toString()).params(params).query((rs, row) -> map(rs)).list();
    }

    private static DepositEscrow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DepositEscrow(rs.getLong("escrow_id"), rs.getLong("transaction_id"),
                rs.getLong("service_client_id"), rs.getString("external_contract_id"),
                rs.getString("contract_type"), integer(rs.getBigDecimal("expected_amount")),
                rs.getString("escrow_status"), rs.getString("transaction_status"),
                rs.getString("idempotency_key"), rs.getString("request_fingerprint"), rs.getString("deposit_step"),
                rs.getString("depositor_name_encrypted"), rs.getString("virtual_account_id"),
                rs.getString("virtual_account_no"), rs.getString("deposit_verification_status"),
                integer(rs.getBigDecimal("verified_deposit_amount")), rs.getObject("depositor_name_matched", Boolean.class),
                rs.getString("source_bank_code"), rs.getString("source_account_encrypted"));
    }

    private static BigInteger integer(BigDecimal value) {
        return value == null ? null : value.toBigIntegerExact();
    }

    public record CancelCommand(long escrowId, String fingerprint) {
    }
}
