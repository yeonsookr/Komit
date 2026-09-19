package com.a501.escrow.domain.account.repository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AccountAuthRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AccountAuthRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AccountAuthRequest> findByIdempotencyKey(long serviceClientId, String key) {
        return query("""
                SELECT id, service_client_id, request_fingerprint, user_key_lookup_hash,
                       user_key_encrypted, account_no_encrypted, status,
                       transaction_unique_no, expires_at
                FROM account_auth_requests
                WHERE service_client_id=:serviceClientId AND idempotency_key=:key
                """, Map.of("serviceClientId", serviceClientId, "key", key));
    }

    public Optional<AccountAuthRequest> findByTransactionNo(long serviceClientId, String transactionNo) {
        return query("""
                SELECT id, service_client_id, request_fingerprint, user_key_lookup_hash,
                       user_key_encrypted, account_no_encrypted, status,
                       transaction_unique_no, expires_at
                FROM account_auth_requests
                WHERE service_client_id=:serviceClientId AND transaction_unique_no=:transactionNo
                """, Map.of("serviceClientId", serviceClientId, "transactionNo", transactionNo));
    }

    public List<AccountAuthRequest> findByUserHash(long serviceClientId, String userHash) {
        return jdbc.query("""
                SELECT id, service_client_id, request_fingerprint, user_key_lookup_hash,
                       user_key_encrypted, account_no_encrypted, status,
                       transaction_unique_no, expires_at
                FROM account_auth_requests
                WHERE service_client_id=:serviceClientId AND user_key_lookup_hash=:userHash
                ORDER BY created_at DESC
                """, Map.of("serviceClientId", serviceClientId, "userHash", userHash),
                (rs, rowNumber) -> map(rs));
    }

    @Transactional
    public boolean insertRequested(
            long serviceClientId,
            String idempotencyKey,
            String fingerprint,
            String userHash,
            String encryptedUserKey,
            String encryptedAccountNo,
            String authText,
            String requestReference
    ) {
        try {
            return jdbc.update("""
                    INSERT INTO account_auth_requests
                      (service_client_id, idempotency_key, request_fingerprint,
                       user_key_lookup_hash, user_key_encrypted, account_no_encrypted,
                       auth_text, request_reference)
                    VALUES (:serviceClientId, :idempotencyKey, :fingerprint,
                            :userHash, :encryptedUserKey, :encryptedAccountNo,
                            :authText, :requestReference)
                    """, Map.of(
                    "serviceClientId", serviceClientId,
                    "idempotencyKey", idempotencyKey,
                    "fingerprint", fingerprint,
                    "userHash", userHash,
                    "encryptedUserKey", encryptedUserKey,
                    "encryptedAccountNo", encryptedAccountNo,
                    "authText", authText,
                    "requestReference", requestReference)) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Transactional
    public boolean claim(long id, String instanceId) {
        return jdbc.update("""
                UPDATE account_auth_requests
                SET status='DISPATCHING', dispatched_by=:instanceId, dispatched_at=clock_timestamp()
                WHERE id=:id AND status='REQUESTED'
                """, Map.of("id", id, "instanceId", instanceId)) == 1;
    }

    @Transactional
    public void succeed(long id, String transactionNo, OffsetDateTime expiresAt) {
        jdbc.update("""
                UPDATE account_auth_requests
                SET status='SUCCEEDED', transaction_unique_no=:transactionNo, expires_at=:expiresAt
                WHERE id=:id AND status='DISPATCHING'
                """, Map.of("id", id, "transactionNo", transactionNo, "expiresAt", expiresAt));
    }

    @Transactional
    public void markUnknown(long id) {
        jdbc.update("""
                UPDATE account_auth_requests SET status='UNKNOWN'
                WHERE id=:id AND status='DISPATCHING'
                """, Map.of("id", id));
    }

    private Optional<AccountAuthRequest> query(String sql, Map<String, ?> parameters) {
        return jdbc.query(sql, parameters, (rs, rowNumber) -> map(rs)).stream().findFirst();
    }

    private static AccountAuthRequest map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AccountAuthRequest(
                rs.getLong("id"), rs.getLong("service_client_id"),
                rs.getString("request_fingerprint"), rs.getString("user_key_lookup_hash"),
                rs.getString("user_key_encrypted"), rs.getString("account_no_encrypted"),
                rs.getString("status"), rs.getString("transaction_unique_no"),
                rs.getObject("expires_at", OffsetDateTime.class));
    }
}
