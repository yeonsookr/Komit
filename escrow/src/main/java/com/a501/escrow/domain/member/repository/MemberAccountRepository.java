package com.a501.escrow.domain.member.repository;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MemberAccountRepository {
    private final JdbcClient jdbc;

    public MemberAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<MemberAccountProvision> findByKey(long clientId, String key) {
        return query("service_client_id=:clientId AND idempotency_key=:value", clientId, key);
    }

    public Optional<MemberAccountProvision> findByUser(long clientId, String userId) {
        return query("service_client_id=:clientId AND user_id=:value", clientId, userId);
    }

    private Optional<MemberAccountProvision> query(String predicate, long clientId, String value) {
        return jdbc.sql("SELECT * FROM member_account_provisions WHERE " + predicate)
                .param("clientId", clientId).param("value", value)
                .query((rs, row) -> new MemberAccountProvision(
                        rs.getLong("id"), rs.getLong("service_client_id"), rs.getString("user_id"),
                        rs.getString("account_type_unique_no"), rs.getString("idempotency_key"),
                        rs.getString("request_fingerprint"), rs.getString("provision_step"),
                        rs.getString("provision_status"), rs.getObject("step_requested_at", java.time.OffsetDateTime.class),
                        rs.getString("user_key_encrypted"), rs.getString("bank_code"),
                        rs.getString("account_no_encrypted"), rs.getLong("seed_amount"),
                        rs.getString("seed_status"), rs.getString("seed_request_reference"),
                        rs.getString("seed_transaction_no"))).optional();
    }

    public void insert(long clientId, String userId, String productNo, String key, String fingerprint, long seedAmount) {
        try {
            jdbc.sql("""
                    INSERT INTO member_account_provisions
                    (service_client_id,user_id,account_type_unique_no,idempotency_key,request_fingerprint,seed_amount)
                    VALUES (:clientId,:userId,:productNo,:key,:fingerprint,:seedAmount)
                    """).param("clientId", clientId).param("userId", userId).param("productNo", productNo)
                    .param("key", key).param("fingerprint", fingerprint).param("seedAmount", seedAmount).update();
        } catch (DuplicateKeyException ignored) {
            // A concurrent identical request is resolved by re-reading both business keys.
        }
    }

    public boolean claim(long id, String step) {
        return jdbc.sql("""
                UPDATE member_account_provisions SET step_requested_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND provision_step=:step AND provision_status='PENDING' AND step_requested_at IS NULL
                """).param("id", id).param("step", step).update() == 1;
    }

    public boolean claimSeed(long id, String reference) {
        return jdbc.sql("""
                UPDATE member_account_provisions SET step_requested_at=CURRENT_TIMESTAMP,
                  seed_request_reference=:reference, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND provision_step='SEED_DEPOSIT' AND provision_status='PENDING'
                  AND step_requested_at IS NULL
                """).param("id", id).param("reference", reference).update() == 1;
    }

    public void userRegistered(long id, String encryptedKey, String keyHash) {
        jdbc.sql("""
                UPDATE member_account_provisions SET provision_step='ACCOUNT_OPEN', provision_status='PENDING', step_requested_at=NULL,
                  user_key_encrypted=:encryptedKey, user_key_lookup_hash=:keyHash, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND provision_step='USER_REGISTER'
                """).param("id", id).param("encryptedKey", encryptedKey).param("keyHash", keyHash).update();
    }

    public void accountOpened(long id, String bankCode, String encryptedAccount) {
        jdbc.sql("""
                UPDATE member_account_provisions SET provision_step='SEED_DEPOSIT', provision_status='PENDING',
                  seed_status='PENDING', step_requested_at=NULL,
                  bank_code=:bankCode, account_no_encrypted=:account, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND provision_step='ACCOUNT_OPEN'
                """).param("id", id).param("bankCode", bankCode).param("account", encryptedAccount).update();
    }

    public void complete(long id, String transactionNo) {
        jdbc.sql("""
                UPDATE member_account_provisions SET provision_step='COMPLETED', provision_status='COMPLETED',
                  step_requested_at=NULL, seed_status='COMPLETED', seed_transaction_no=:transactionNo,
                  seeded_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND provision_step='SEED_DEPOSIT'
                """).param("id", id).param("transactionNo", transactionNo).update();
    }

    public void markUnknown(long id, String step) {
        String seed = "SEED_DEPOSIT".equals(step) ? ", seed_status='UNKNOWN'" : "";
        jdbc.sql("UPDATE member_account_provisions SET provision_status='UNKNOWN'" + seed
                        + ", updated_at=CURRENT_TIMESTAMP WHERE id=:id AND provision_step=:step")
                .param("id", id).param("step", step).update();
    }
}
