package com.a501.escrow.domain.escrow.repository;

import java.util.Map;
import java.util.Optional;

import com.a501.escrow.domain.common.Money;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EscrowRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public EscrowRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 소유 서비스를 함께 검사하며 계약 행을 잠급니다. 상태 변경 트랜잭션 안에서만 호출할 수 있습니다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<LockedEscrow> findOwnedByIdForUpdate(long serviceClientId, long escrowId) {
        String sql = """
                SELECT id, service_client_id, external_contract_id, expected_amount, status, hold_status,
                       external_report_id
                FROM escrows
                WHERE id = :escrowId AND service_client_id = :serviceClientId
                FOR UPDATE
                """;

        return jdbcTemplate.query(sql, Map.of(
                        "escrowId", escrowId,
                        "serviceClientId", serviceClientId
                ), (resultSet, rowNumber) -> new LockedEscrow(
                        resultSet.getLong("id"),
                        resultSet.getLong("service_client_id"),
                        resultSet.getString("external_contract_id"),
                        Money.fromDatabase(resultSet.getBigDecimal("expected_amount")),
                        resultSet.getString("status"),
                        resultSet.getString("hold_status"),
                        resultSet.getString("external_report_id")
                ))
                .stream()
                .findFirst();
    }
}
