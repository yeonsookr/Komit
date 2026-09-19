package com.a501.escrow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import com.a501.escrow.common.api.EscrowErrorResponse;
import com.a501.escrow.common.api.EscrowHeaderFactory;
import com.a501.escrow.common.api.EscrowRequestHeader;
import com.a501.escrow.common.crypto.ClientApiKeyHasher;
import com.a501.escrow.common.crypto.FieldEncryption;
import com.a501.escrow.common.crypto.RequestFingerprint;
import com.a501.escrow.common.crypto.SensitiveDataHasher;
import com.a501.escrow.common.exception.EscrowApiException;
import com.a501.escrow.common.security.ApiRequirements;
import com.a501.escrow.common.security.AuthenticatedCaller;
import com.a501.escrow.common.security.InternalApiSecurity;
import com.a501.escrow.common.security.WriterGuard;
import com.a501.escrow.config.AppProperties;
import com.a501.escrow.config.InstanceRole;
import com.a501.escrow.domain.common.Money;
import com.a501.escrow.domain.escrow.repository.EscrowRepository;
import com.a501.escrow.domain.finance.repository.FinancialOperationAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "app.instance-role=WRITER",
        "app.public-base-url=http://localhost:8081",
        "app.security.operator-api-key=dev-only-operator-client-api-key"
})
class DataAccessIntegrationTests {

    static PostgreSQLContainer postgres;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (System.getenv("CI") != null) {
            String database = environmentOrDefault("POSTGRES_DB", "escrow");
            String url = environmentOrDefault(
                    "DB_URL", "jdbc:postgresql://postgres:5432/" + database);
            String username = environmentOrDefault(
                    "DB_USERNAME", environmentOrDefault("POSTGRES_USER", "escrow"));
            String password = environmentOrDefault(
                    "DB_PASSWORD", environmentOrDefault("POSTGRES_PASSWORD", "escrow"));

            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> username);
            registry.add("spring.datasource.password", () -> password);
            registry.add("app.datasource.read-url", () -> url);
            return;
        }

        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.datasource.read-url", postgres::getJdbcUrl);
    }

    @AfterAll
    static void stopLocalPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EscrowRepository escrowRepository;

    @Autowired
    FinancialOperationAttemptRepository attemptRepository;

    @Autowired
    ClientApiKeyHasher apiKeyHasher;

    @Autowired
    InternalApiSecurity internalApiSecurity;

    @Autowired
    FieldEncryption fieldEncryption;

    @Autowired
    RequestFingerprint requestFingerprint;

    @Autowired
    SensitiveDataHasher sensitiveDataHasher;

    @Autowired
    EscrowHeaderFactory headerFactory;

    @Autowired
    tools.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM financial_operation_attempts");
        jdbcTemplate.update("DELETE FROM escrow_transactions");
        jdbcTemplate.update("DELETE FROM escrows");
        jdbcTemplate.update("DELETE FROM member_account_provisions");
        jdbcTemplate.update("DELETE FROM service_clients");

        jdbcTemplate.update(
                "INSERT INTO service_clients (id, name, api_key_hash) VALUES (1, 'Komit', ?)",
                apiKeyHasher.hash("test-client-api-key"));
        jdbcTemplate.update("""
                INSERT INTO member_account_provisions
                    (id, service_client_id, user_id, account_type_unique_no,
                     idempotency_key, request_fingerprint, seed_amount)
                VALUES (1, 1, 'member@example.com', 'product', 'provision-key', repeat('a', 64), 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO escrows
                    (id, service_client_id, payer_provision_id, external_contract_id,
                     contract_type, expected_amount)
                VALUES (1, 1, 1, 'CONTRACT-1', 'DEVELOPMENT', 9999999999999999999)
                """);
        jdbcTemplate.update("""
                INSERT INTO escrow_transactions
                    (id, service_client_id, escrow_id, transaction_type, amount,
                     external_reference_id, idempotency_key, request_fingerprint,
                     depositor_name_encrypted)
                VALUES (1, 1, 1, 'DEPOSIT', 1, 'INITIAL', 'deposit-key', repeat('b', 64), 'encrypted')
                """);
        jdbcTemplate.update("""
                INSERT INTO financial_operation_attempts
                    (id, transaction_id, step, attempt_no, request_reference)
                VALUES (1, 1, 'DEPOSIT_TRANSFER', 1, 'request-1')
                """);
    }

    @Test
    void flywayAppliesV1ToAnEmptyDatabase() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('service_clients', 'escrows', 'financial_operation_attempts')
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(3);
    }

    @Test
    void lockRequiresTransactionAndPreservesNumeric19Range() {
        assertThatThrownBy(() -> escrowRepository.findOwnedByIdForUpdate(1, 1))
                .isInstanceOf(IllegalTransactionStateException.class);

        var locked = new TransactionTemplate(transactionManager)
                .execute(status -> escrowRepository.findOwnedByIdForUpdate(1, 1).orElseThrow());
        var notOwned = new TransactionTemplate(transactionManager)
                .execute(status -> escrowRepository.findOwnedByIdForUpdate(2, 1));

        assertThat(locked.expectedAmount())
                .isEqualTo(new Money(new BigInteger("9999999999999999999")));
        assertThat(notOwned).isNotPresent();
    }

    @Test
    void onlyOneCallerCanClaimRequestedAttempt() {
        var first = attemptRepository.claim(1, "writer-1");
        var second = attemptRepository.claim(1, "writer-2");

        assertThat(first).isPresent();
        assertThat(first.orElseThrow().requestReference()).isEqualTo("request-1");
        assertThat(second).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT dispatched_by FROM financial_operation_attempts WHERE id = 1", String.class))
                .isEqualTo("writer-1");
    }

    @Test
    void readOnlyTransactionUsesReadOnlyConnection() {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        Boolean readOnlyConnection = readOnly.execute(status -> jdbcTemplate.queryForObject(
                "SELECT current_setting('transaction_read_only') = 'on'", Boolean.class));

        TransactionTemplate writable = new TransactionTemplate(transactionManager);
        Boolean writableConnection = writable.execute(status -> jdbcTemplate.queryForObject(
                "SELECT current_setting('transaction_read_only') = 'off'", Boolean.class));

        assertThat(readOnlyConnection).isTrue();
        assertThat(writableConnection).isTrue();
    }

    @Test
    void authenticatesActiveClientAndRejectsInvalidOrOperatorKey() {
        var caller = internalApiSecurity.authenticate(
                validHeader("inquireEscrow", "test-client-api-key", null, null),
                "inquireEscrow",
                ApiRequirements.read());

        assertThat(caller).isEqualTo(new AuthenticatedCaller.Client(1, "Komit"));
        assertThatThrownBy(() -> internalApiSecurity.authenticate(
                validHeader("inquireEscrow", "wrong-key", null, null),
                "inquireEscrow",
                ApiRequirements.read()))
                .isInstanceOfSatisfying(EscrowApiException.class,
                        exception -> assertThat(exception.responseCode()).isEqualTo("E5001"));
        assertThatThrownBy(() -> internalApiSecurity.authenticate(
                validHeader("inquireEscrow", "dev-only-operator-client-api-key", null, null),
                "inquireEscrow",
                ApiRequirements.read()))
                .isInstanceOfSatisfying(EscrowApiException.class,
                        exception -> assertThat(exception.responseCode()).isEqualTo("E5001"));
    }

    @Test
    void operatorKeyIsAcceptedOnlyForOperatorApi() {
        var caller = internalApiSecurity.authenticate(
                validHeader(
                        "inquireReconciliation",
                        "dev-only-operator-client-api-key",
                        null,
                        null),
                "inquireReconciliation",
                ApiRequirements.operatorRead());

        assertThat(caller).isInstanceOf(AuthenticatedCaller.Operator.class);
    }

    @Test
    void validatesMockHeaderRulesAndReaderRejectsWrites() {
        assertThatThrownBy(() -> internalApiSecurity.authenticate(
                validHeader("wrongApiName", "test-client-api-key", null, null),
                "inquireEscrow",
                ApiRequirements.read()))
                .isInstanceOfSatisfying(EscrowApiException.class,
                        exception -> assertThat(exception.responseCode()).isEqualTo("H1001"));

        assertThatThrownBy(() -> internalApiSecurity.authenticate(
                validHeader("createEscrow", "test-client-api-key", null, "user-key"),
                "createEscrow",
                ApiRequirements.write(true)))
                .isInstanceOfSatisfying(EscrowApiException.class,
                        exception -> assertThat(exception.responseCode()).isEqualTo("E5002"));

        WriterGuard readerGuard = new WriterGuard(
                new AppProperties(URI.create("http://localhost:8081"), InstanceRole.READER));
        assertThatThrownBy(() -> readerGuard.requireWriter(
                validHeader("createEscrow", "test-client-api-key", "key-1", "user-key")))
                .isInstanceOfSatisfying(EscrowApiException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(503);
                    assertThat(exception.responseCode()).isEqualTo("Q1000");
                });
    }

    @Test
    void fingerprintExcludesTransportHeaderAndEncryptionRoundTrips() throws Exception {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("Header", Map.of("transmissionTime", "120000"));
        first.put("amount", 1000);
        first.put("contractId", "CONTRACT-1");
        Map<String, Object> retry = new LinkedHashMap<>();
        retry.put("contractId", "CONTRACT-1");
        retry.put("amount", 1000);
        retry.put("Header", Map.of("transmissionTime", "120100"));

        assertThat(requestFingerprint.create(first)).isEqualTo(requestFingerprint.create(retry));
        assertThat(requestFingerprint.create(Map.of("amount", 1001, "contractId", "CONTRACT-1")))
                .isNotEqualTo(requestFingerprint.create(first));

        String encrypted = fieldEncryption.encrypt("0011234567890123");
        assertThat(encrypted).startsWith("v1:").doesNotContain("0011234567890123");
        assertThat(fieldEncryption.decrypt(encrypted)).isEqualTo("0011234567890123");
        assertThat(sensitiveDataHasher.userKeyLookupHash(1, "user-key"))
                .hasSize(64)
                .isNotEqualTo(sensitiveDataHasher.userKeyLookupHash(2, "user-key"));

        String errorJson = objectMapper.writeValueAsString(new EscrowErrorResponse(
                headerFactory.error("E5001", "CLIENT API KEY가 유효하지 않습니다.",
                        validHeader("inquireEscrow", "wrong-key", null, null))));
        assertThat(errorJson).contains("\"Header\"").doesNotContain("REC");
        assertThat(errorJson).doesNotContain("\"header\"");
    }

    private static EscrowRequestHeader validHeader(
            String apiName,
            String clientApiKey,
            String idempotencyKey,
            String userKey
    ) {
        return new EscrowRequestHeader(
                apiName,
                "20260919",
                "120000",
                "00100",
                "001",
                apiName,
                "20260919120000000001",
                clientApiKey,
                idempotencyKey,
                userKey);
    }
}
