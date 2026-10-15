package com.a501.escrow.domain.finance.repository;

import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FinancialOperationAttemptRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FinancialOperationAttemptRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * REQUESTED 호출의 실행권을 한 인스턴스에만 부여합니다.
     * 반환값이 비어 있으면 다른 실행자가 먼저 획득했거나 현재 상태가 REQUESTED가 아닙니다.
     */
    @Transactional
    public Optional<ClaimedFinancialOperation> claim(long attemptId, String instanceId) {
        String sql = """
                UPDATE financial_operation_attempts
                SET status = 'DISPATCHING',
                    dispatched_by = :instanceId,
                    dispatched_at = clock_timestamp()
                WHERE id = :attemptId AND status = 'REQUESTED'
                RETURNING id, request_reference
                """;

        return jdbcTemplate.query(sql, Map.of(
                        "attemptId", attemptId,
                        "instanceId", instanceId
                ), (resultSet, rowNumber) -> new ClaimedFinancialOperation(
                        resultSet.getLong("id"),
                        resultSet.getString("request_reference")
                ))
                .stream()
                .findFirst();
    }
}
