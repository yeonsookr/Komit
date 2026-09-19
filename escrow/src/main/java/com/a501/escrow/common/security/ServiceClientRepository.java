package com.a501.escrow.common.security;

import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceClientRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ServiceClientRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 키 폐기·서비스 정지를 즉시 반영해야 하므로 readOnly 트랜잭션(Standby)을 사용하지 않습니다.
    public Optional<ServiceClient> findActiveByApiKeyHash(String apiKeyHash) {
        return jdbcTemplate.query("""
                        SELECT id, name
                        FROM service_clients
                        WHERE api_key_hash = :apiKeyHash AND status = 'ACTIVE'
                        """, Map.of("apiKeyHash", apiKeyHash),
                (resultSet, rowNumber) -> new ServiceClient(
                        resultSet.getLong("id"),
                        resultSet.getString("name")))
                .stream()
                .findFirst();
    }
}
