package com.a501.escrow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
import com.a501.escrow.domain.escrow.repository.EscrowDepositRepository;
import com.a501.escrow.domain.escrow.dto.EscrowHoldDtos;
import com.a501.escrow.domain.escrow.dto.EscrowDtos;
import com.a501.escrow.domain.escrow.dto.EscrowSettlementDtos;
import com.a501.escrow.domain.escrow.service.EscrowDepositService;
import com.a501.escrow.domain.escrow.service.EscrowHoldService;
import com.a501.escrow.domain.escrow.service.EscrowSettlementService;
import com.a501.escrow.domain.escrow.repository.EscrowSettlementRepository;
import com.a501.escrow.domain.finance.repository.FinancialOperationAttemptRepository;
import com.a501.escrow.integration.finance.FinanceClient;
import com.a501.escrow.domain.reconciliation.dto.ReconciliationDtos;
import com.a501.escrow.domain.reconciliation.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.profiles.active=prod",
        "app.instance-role=WRITER",
        "app.public-base-url=http://localhost:8081",
        "app.security.client-api-key-hmac-secret=test-client-api-key-hmac-secret-0001",
        "app.security.sensitive-data-hmac-secret=test-sensitive-data-hmac-secret-0001",
        "app.security.encryption-key-base64=ZGV2LW9ubHktMzItYnl0ZS1hZXMta2V5LTAwMDAwMDA=",
        "app.security.operator-api-key=dev-only-operator-client-api-key",
        "app.finance.api-key=test-finance-api-key",
        "app.finance.account-type-unique-no=test-product",
        "app.finance.escrow-user-key=test-escrow-user-key",
        "app.finance.escrow-account-no=0010000000000000",
        "app.finance.fee-account-no=0010000000000001",
        "app.finance.virtual-account-validity-days=30"
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
    EscrowDepositRepository escrowDepositRepository;

    @Autowired
    EscrowHoldService escrowHoldService;

    @Autowired
    EscrowDepositService escrowDepositService;

    @Autowired
    EscrowSettlementService escrowSettlementService;

    @Autowired
    EscrowSettlementRepository escrowSettlementRepository;

    @Autowired
    ReconciliationService reconciliationService;

    @MockitoBean
    FinanceClient financeClient;

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
        jdbcTemplate.update("DELETE FROM reconciliation_snapshots");
        jdbcTemplate.update("DELETE FROM escrow_state_commands");
        jdbcTemplate.update("DELETE FROM financial_operation_attempts");
        jdbcTemplate.update("DELETE FROM escrow_transactions");
        jdbcTemplate.update("DELETE FROM settlements");
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
        // The fixture uses explicit IDs; advance identity sequences so repository inserts behave as production does.
        jdbcTemplate.queryForObject(
                "SELECT setval(pg_get_serial_sequence('escrows','id'), (SELECT max(id) FROM escrows), true)",
                Long.class);
        jdbcTemplate.queryForObject(
                "SELECT setval(pg_get_serial_sequence('escrow_transactions','id'), "
                        + "(SELECT max(id) FROM escrow_transactions), true)", Long.class);
        jdbcTemplate.queryForObject(
                "SELECT setval(pg_get_serial_sequence('financial_operation_attempts','id'), "
                        + "(SELECT max(id) FROM financial_operation_attempts), true)", Long.class);
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

        assertThat(migrationCount).isEqualTo(2);
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
    void persistsDepositStateMachineAndActivatesOnlyAfterSettlement() {
        String depositor = fieldEncryption.encrypt("김의뢰");
        String account = fieldEncryption.encrypt("0012222222222222");
        escrowDepositRepository.create(1, 1, "CONTRACT-DEPOSIT", "DEVELOPMENT",
                BigInteger.valueOf(650_000), "deposit-idempotency", "c".repeat(64),
                depositor, "001", account);

        var created = escrowDepositRepository.findByContractId(1, "CONTRACT-DEPOSIT").orElseThrow();
        assertThat(created.escrowStatus()).isEqualTo("CREATED");
        assertThat(created.transactionStatus()).isEqualTo("PENDING");

        escrowDepositRepository.virtualAccountCreated(created.transactionId(), "va-1", "0011111111111111");
        escrowDepositRepository.transferStarted(created.transactionId());
        escrowDepositRepository.transferred(created.transactionId(), "bank-tx-1");
        escrowDepositRepository.verified(created.transactionId(), BigInteger.valueOf(650_000), true, true);
        assertThat(escrowDepositRepository.findById(1, created.escrowId()).orElseThrow().escrowStatus())
                .isEqualTo("CREATED");

        escrowDepositRepository.settled(created.escrowId(), created.transactionId(), BigInteger.valueOf(650_000));
        var active = escrowDepositRepository.findById(1, created.escrowId()).orElseThrow();
        assertThat(active.escrowStatus()).isEqualTo("ACTIVE");
        assertThat(active.transactionStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void cancelsOnlyBeforeAnyDepositDispatchAndReplaysCommand() {
        escrowDepositRepository.create(1, 1, "CONTRACT-CANCEL", "DEVELOPMENT",
                BigInteger.valueOf(100_000), "cancel-create-key", "d".repeat(64),
                fieldEncryption.encrypt("김의뢰"), "001", fieldEncryption.encrypt("0012222222222222"));
        var created = escrowDepositRepository.findByContractId(1, "CONTRACT-CANCEL").orElseThrow();

        assertThat(escrowDepositRepository.cancel(1, created.escrowId(), "cancel-key", "e".repeat(64)))
                .isTrue();
        assertThat(escrowDepositRepository.findCancelCommand(1, "cancel-key"))
                .hasValueSatisfying(command -> {
                    assertThat(command.escrowId()).isEqualTo(created.escrowId());
                    assertThat(command.fingerprint()).isEqualTo("e".repeat(64));
                });
        assertThat(escrowDepositRepository.findById(1, created.escrowId()).orElseThrow().escrowStatus())
                .isEqualTo("CANCELLED");
    }

    @Test
    void holdsAndReleasesActiveEscrowAndReplaysHistoricalResults() {
        jdbcTemplate.update("UPDATE escrows SET status='ACTIVE' WHERE id=1");
        var hold = new EscrowHoldDtos.Request(
                validHeader("holdEscrow", "test-client-api-key", "hold-key", null),
                1, "REPORT-7");
        var unhold = new EscrowHoldDtos.Request(
                validHeader("unholdEscrow", "test-client-api-key", "unhold-key", null),
                1, "REPORT-7");

        assertThat(escrowHoldService.hold(hold).isHeld()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT hold_status FROM escrows WHERE id=1", String.class)).isEqualTo("HELD");
        assertThat(escrowDepositService.inquire(new EscrowDtos.InquireRequest(
                validHeader("inquireEscrow", "test-client-api-key", null, null), 1, "CONTRACT-1")).isHeld())
                .isTrue();
        assertThat(escrowHoldService.hold(hold).isHeld()).isTrue();

        assertThat(escrowHoldService.unhold(unhold).isHeld()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT hold_status FROM escrows WHERE id=1", String.class)).isEqualTo("RELEASED");
        assertThat(escrowDepositService.inquire(new EscrowDtos.InquireRequest(
                validHeader("inquireEscrow", "test-client-api-key", null, null), 1, "CONTRACT-1")).isHeld())
                .isFalse();
        assertThat(escrowHoldService.hold(hold).isHeld()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT hold_status FROM escrows WHERE id=1", String.class)).isEqualTo("RELEASED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM escrow_state_commands WHERE escrow_id=1", Integer.class)).isEqualTo(2);
    }

    @Test
    void rejectsConflictingReportAndClosedOrUnfundedEscrow() {
        jdbcTemplate.update("UPDATE escrows SET status='ACTIVE' WHERE id=1");
        escrowHoldService.hold(new EscrowHoldDtos.Request(
                validHeader("holdEscrow", "test-client-api-key", "hold-key", null),
                1, "REPORT-7"));

        assertThatThrownBy(() -> escrowHoldService.hold(new EscrowHoldDtos.Request(
                validHeader("holdEscrow", "test-client-api-key", "other-key", null),
                1, "REPORT-8")))
                .isInstanceOfSatisfying(EscrowApiException.class,
                        exception -> assertThat(exception.responseCode()).isEqualTo("E5014"));
        assertThatThrownBy(() -> escrowHoldService.unhold(new EscrowHoldDtos.Request(
                validHeader("unholdEscrow", "test-client-api-key", "unhold-key", null),
                1, "REPORT-8")))
                .isInstanceOfSatisfying(EscrowApiException.class,
                        exception -> assertThat(exception.responseCode()).isEqualTo("E5014"));

        jdbcTemplate.update("UPDATE escrows SET hold_status='NONE',external_report_id=NULL,held_at=NULL WHERE id=1");
        jdbcTemplate.update("UPDATE escrows SET status='CREATED' WHERE id=1");
        assertThatThrownBy(() -> escrowHoldService.hold(new EscrowHoldDtos.Request(
                validHeader("holdEscrow", "test-client-api-key", "created-key", null),
                1, "REPORT-9")))
                .isInstanceOfSatisfying(EscrowApiException.class,
                        exception -> assertThat(exception.responseCode()).isEqualTo("E5007"));
    }

    @Test
    void releasesNetAndFeeThenRefundsOnlyTheFullRemainingBalance() {
        String payerAccount = "0012222222222222";
        String workerAccount = "0013333333333333";
        jdbcTemplate.update("""
                UPDATE escrows SET expected_amount=650000,status='ACTIVE' WHERE id=1
                """);
        jdbcTemplate.update("""
                UPDATE escrow_transactions SET amount=650000,status='COMPLETED',deposit_verification_status='MATCHED',
                    source_bank_code='001',source_account_encrypted=?,completed_at=clock_timestamp() WHERE id=1
                """, fieldEncryption.encrypt(payerAccount));

        when(financeClient.inquireAccountHolder(anyString(), anyString())).thenAnswer(invocation -> {
            String account = invocation.getArgument(1);
            String name = account.equals(workerAccount) ? "worker" : account.equals(payerAccount) ? "payer" : "KOMIT";
            return new FinanceClient.AccountHolder("001", account, name);
        });
        when(financeClient.transfer(anyString(), anyString(), anyString(), any(BigInteger.class),
                anyString(), anyString(), anyString())).thenReturn("bank-release", "bank-fee", "bank-refund");

        var released = escrowSettlementService.release(new EscrowSettlementDtos.ReleaseRequest(
                validHeader("releaseEscrow", "test-client-api-key", "release-key", null),
                1, "SETTLEMENT:9001", BigInteger.valueOf(400_000), "001", workerAccount, "worker"));

        assertThat(released.status()).isEqualTo("COMPLETED");
        assertThat(released.netAmount()).isEqualTo(BigInteger.valueOf(396_000));
        assertThat(released.feeAmount()).isEqualTo(BigInteger.valueOf(4_000));
        assertThat(released.releasedAmount()).isEqualTo(BigInteger.valueOf(400_000));
        assertThat(released.balance()).isEqualTo(BigInteger.valueOf(250_000));
        assertThat(escrowSettlementRepository.totals(1).released()).isEqualTo(BigInteger.valueOf(396_000));
        assertThat(escrowSettlementService.release(new EscrowSettlementDtos.ReleaseRequest(
                validHeader("releaseEscrow", "test-client-api-key", "release-key", null),
                1, "SETTLEMENT:9001", BigInteger.valueOf(400_000), "001", workerAccount, "worker")))
                .isEqualTo(released);

        assertThatThrownBy(() -> escrowSettlementService.refund(new EscrowSettlementDtos.RefundRequest(
                validHeader("refundEscrow", "test-client-api-key", "partial-refund", null),
                1, BigInteger.valueOf(200_000), "001", payerAccount, "payer", "SETTLEMENT:9001")))
                .isInstanceOfSatisfying(EscrowApiException.class,
                        exception -> assertThat(exception.responseCode()).isEqualTo("E5013"));

        var refunded = escrowSettlementService.refund(new EscrowSettlementDtos.RefundRequest(
                validHeader("refundEscrow", "test-client-api-key", "refund-key", null),
                1, BigInteger.valueOf(250_000), "001", payerAccount, "payer", "SETTLEMENT:9001"));

        assertThat(refunded.status()).isEqualTo("COMPLETED");
        assertThat(refunded.refundedAmount()).isEqualTo(BigInteger.valueOf(250_000));
        assertThat(refunded.balance()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM escrows WHERE id=1", String.class))
                .isEqualTo("REFUNDED");
        assertThat(escrowDepositService.inquire(new EscrowDtos.InquireRequest(
                validHeader("inquireEscrow", "test-client-api-key", null, null), 1, "CONTRACT-1")))
                .satisfies(detail -> {
                    assertThat(detail.releasedAmount()).isEqualTo(BigInteger.valueOf(396_000));
                    assertThat(detail.feeAmount()).isEqualTo(BigInteger.valueOf(4_000));
                    assertThat(detail.refundedAmount()).isEqualTo(BigInteger.valueOf(250_000));
                    assertThat(detail.balance()).isZero();
                });
    }

    @Test
    void reconcilesAllServiceLedgerAgainstActualCustodyBalanceWithOperatorKey() {
        jdbcTemplate.update("""
                UPDATE escrow_transactions SET status='COMPLETED',deposit_verification_status='MATCHED',
                    completed_at=clock_timestamp() WHERE id=1
                """);
        when(financeClient.inquireAccountBalance("test-escrow-user-key", "0010000000000000"))
                .thenReturn(BigInteger.ONE);

        var result = reconciliationService.inquire(new ReconciliationDtos.Request(
                validHeader("inquireReconciliation", "dev-only-operator-client-api-key", null, null), 7));

        assertThat(result.ledgerTotal()).isEqualTo(BigInteger.ONE);
        assertThat(result.bankBalance()).isEqualTo(BigInteger.ONE);
        assertThat(result.differenceAmount()).isZero();
        assertThat(result.result()).isEqualTo("MATCHED");
        assertThat(result.escrowCount()).isEqualTo(1);
        assertThat(result.history()).hasSize(1);

        assertThatThrownBy(() -> reconciliationService.inquire(new ReconciliationDtos.Request(
                validHeader("inquireReconciliation", "test-client-api-key", null, null), 7)))
                .isInstanceOfSatisfying(EscrowApiException.class,
                        exception -> assertThat(exception.responseCode()).isEqualTo("E5001"));
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
