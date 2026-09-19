package com.a501.escrow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;

import com.a501.escrow.domain.common.Money;
import com.a501.escrow.domain.escrow.repository.EscrowRepository;
import com.a501.escrow.domain.finance.repository.FinancialOperationAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = {
        "app.instance-role=WRITER",
        "app.public-base-url=http://localhost:8081"
})
class DataAccessIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18.6"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.datasource.read-url", POSTGRES::getJdbcUrl);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EscrowRepository escrowRepository;

    @Autowired
    FinancialOperationAttemptRepository attemptRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM financial_operation_attempts");
        jdbcTemplate.update("DELETE FROM escrow_transactions");
        jdbcTemplate.update("DELETE FROM escrows");
        jdbcTemplate.update("DELETE FROM member_account_provisions");
        jdbcTemplate.update("DELETE FROM service_clients");

        jdbcTemplate.update("INSERT INTO service_clients (id, name, api_key_hash) VALUES (1, 'Komit', 'hash')");
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
}
