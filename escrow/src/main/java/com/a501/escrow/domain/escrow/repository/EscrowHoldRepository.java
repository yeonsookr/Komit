package com.a501.escrow.domain.escrow.repository;

import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EscrowHoldRepository {
    private static final String CLIENT_ID = "clientId";
    private static final String ESCROW_ID = "escrowId";
    private static final String REPORT_ID = "reportId";

    private final JdbcClient jdbc;

    public EscrowHoldRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StateCommand> findCommand(long clientId, String apiName, String idempotencyKey) {
        return jdbc.sql("""
                SELECT escrow_id,request_fingerprint,external_report_id,result_state
                FROM escrow_state_commands
                WHERE service_client_id=:clientId AND api_name=:apiName AND idempotency_key=:key
                """).params(Map.of(CLIENT_ID, clientId, "apiName", apiName, "key", idempotencyKey))
                .query((rs, row) -> new StateCommand(rs.getLong("escrow_id"),
                        rs.getString("request_fingerprint"), rs.getString("external_report_id"),
                        rs.getString("result_state"))).optional();
    }

    public void hold(long clientId, long escrowId, String reportId, String apiName,
            String idempotencyKey, String fingerprint) {
        jdbc.sql("""
                UPDATE escrows SET hold_status='HELD',external_report_id=:reportId,
                  held_at=clock_timestamp(),released_at=NULL,updated_at=clock_timestamp()
                WHERE id=:escrowId AND service_client_id=:clientId
                """).params(Map.of(CLIENT_ID, clientId, ESCROW_ID, escrowId, REPORT_ID, reportId)).update();
        command(clientId, escrowId, reportId, apiName, idempotencyKey, fingerprint, "HELD");
    }

    public void recordExistingHold(long clientId, long escrowId, String reportId, String apiName,
            String idempotencyKey, String fingerprint) {
        command(clientId, escrowId, reportId, apiName, idempotencyKey, fingerprint, "HELD");
    }

    public void release(long clientId, long escrowId, String reportId, String apiName,
            String idempotencyKey, String fingerprint) {
        jdbc.sql("""
                UPDATE escrows SET hold_status='RELEASED',released_at=clock_timestamp(),updated_at=clock_timestamp()
                WHERE id=:escrowId AND service_client_id=:clientId AND hold_status='HELD'
                  AND external_report_id=:reportId
                """).params(Map.of(CLIENT_ID, clientId, ESCROW_ID, escrowId, REPORT_ID, reportId)).update();
        command(clientId, escrowId, reportId, apiName, idempotencyKey, fingerprint, "RELEASED");
    }

    private void command(long clientId, long escrowId, String reportId, String apiName,
            String idempotencyKey, String fingerprint, String resultState) {
        jdbc.sql("""
                INSERT INTO escrow_state_commands(service_client_id,escrow_id,api_name,idempotency_key,
                  request_fingerprint,external_report_id,result_state)
                VALUES (:clientId,:escrowId,:apiName,:key,:fingerprint,:reportId,:resultState)
                """).params(Map.of(CLIENT_ID, clientId, ESCROW_ID, escrowId, "apiName", apiName,
                        "key", idempotencyKey, "fingerprint", fingerprint,
                        REPORT_ID, reportId, "resultState", resultState)).update();
    }

    public record StateCommand(long escrowId, String fingerprint, String reportId, String resultState) {
    }
}
