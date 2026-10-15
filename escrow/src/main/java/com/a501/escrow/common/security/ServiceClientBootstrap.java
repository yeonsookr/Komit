package com.a501.escrow.common.security;

import java.util.Map;

import com.a501.escrow.common.crypto.ClientApiKeyHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배포 Secret으로 전달된 Backend 전용 키를 원문 저장 없이 서비스 클라이언트로 등록합니다.
 */
@Component
public class ServiceClientBootstrap implements ApplicationRunner {
    private static final String SERVICE_NAME = "Komit Backend";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ClientApiKeyHasher apiKeyHasher;
    private final String clientApiKey;
    private final String oneWonAuthMessage;

    public ServiceClientBootstrap(
            NamedParameterJdbcTemplate jdbcTemplate,
            ClientApiKeyHasher apiKeyHasher,
            @Value("${app.security.bootstrap-client-api-key:}") String clientApiKey,
            @Value("${app.security.bootstrap-client-one-won-auth-message:}") String oneWonAuthMessage
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.apiKeyHasher = apiKeyHasher;
        this.clientApiKey = clientApiKey;
        this.oneWonAuthMessage = oneWonAuthMessage;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (clientApiKey.isBlank()) {
            return;
        }
        if (oneWonAuthMessage.isBlank() || oneWonAuthMessage.length() > 100) {
            throw new IllegalStateException("Backend 1원 인증 문구는 1자 이상 100자 이하여야 합니다.");
        }
        String apiKeyHash = apiKeyHasher.hash(clientApiKey);
        Map<String, Object> parameters = Map.of(
                "name", SERVICE_NAME,
                "apiKeyHash", apiKeyHash,
                "authMessage", oneWonAuthMessage);
        int updated = jdbcTemplate.update("""
                UPDATE service_clients
                SET api_key_hash = :apiKeyHash,
                    status = 'ACTIVE',
                    one_won_auth_message = :authMessage,
                    updated_at = CURRENT_TIMESTAMP
                WHERE name = :name
                """, parameters);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO service_clients (name, api_key_hash, status, one_won_auth_message)
                    VALUES (:name, :apiKeyHash, 'ACTIVE', :authMessage)
                    ON CONFLICT (api_key_hash) DO UPDATE
                    SET name = EXCLUDED.name,
                        status = 'ACTIVE',
                        one_won_auth_message = EXCLUDED.one_won_auth_message,
                        updated_at = CURRENT_TIMESTAMP
                    """, parameters);
        }
    }
}
