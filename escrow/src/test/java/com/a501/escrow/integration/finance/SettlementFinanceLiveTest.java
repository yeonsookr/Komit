package com.a501.escrow.integration.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.a501.escrow.config.FinanceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 실제 송금 없이 지급·환불 전 필수 단계인 SSAFY 예금주 조회만 검증한다. */
@EnabledIfEnvironmentVariable(named = "SSAFY_SETTLEMENT_LIVE_TEST", matches = "true")
class SettlementFinanceLiveTest {
    private static final DateTimeFormatter REFERENCE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSSSS");

    @Test
    void readsEscrowAndConfiguredFeeAccountHolders() {
        String apiKey = required("SSAFY_FINANCE_API_KEY");
        String userKey = required("SSAFY_ESCROW_USER_KEY");
        String escrowAccount = required("SSAFY_ESCROW_ACCOUNT_NO");
        String feeAccount = System.getenv("SSAFY_FEE_ACCOUNT_NO");
        SsafyFinanceClient client = new SsafyFinanceClient(RestClient.builder(), JsonMapper.builder().build(),
                new FinanceProperties(true, "https://finopenapi.ssafy.io/ssafy", apiKey,
                        "Asia/Seoul", "unused", userKey, escrowAccount,
                        feeAccount == null || feeAccount.isBlank() ? "0010000000000001" : feeAccount, 30),
                Clock.systemUTC());

        FinanceClient.AccountHolder escrowHolder = client.inquireAccountHolder(userKey, escrowAccount);
        var escrowBalance = client.inquireAccountBalance(userKey, escrowAccount);
        assertThat(escrowHolder.accountNo()).isEqualTo(escrowAccount);
        assertThat(escrowHolder.bankCode()).isEqualTo("001");
        assertThat(escrowHolder.userName()).isNotBlank();
        assertThat(escrowBalance).isNotNegative();

        if (feeAccount != null && !feeAccount.isBlank()) {
            FinanceClient.AccountHolder feeHolder = client.inquireAccountHolder(userKey, feeAccount);
            var feeBalance = client.inquireAccountBalance(userKey, feeAccount);
            assertThat(feeHolder.accountNo()).isEqualTo(feeAccount).isNotEqualTo(escrowAccount);
            assertThat(feeHolder.userName()).isEqualTo(escrowHolder.userName());
            assertThat(feeBalance).isNotNegative();
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SSAFY_SETTLEMENT_WRITE_LIVE_TEST", matches = "true")
    void transfersOneWonToFeeAccountAndRestoresOriginalBalances() {
        String apiKey = required("SSAFY_FINANCE_API_KEY");
        String userKey = required("SSAFY_ESCROW_USER_KEY");
        String escrowAccount = required("SSAFY_ESCROW_ACCOUNT_NO");
        String feeAccount = required("SSAFY_FEE_ACCOUNT_NO");
        SsafyFinanceClient client = new SsafyFinanceClient(RestClient.builder(), JsonMapper.builder().build(),
                new FinanceProperties(true, "https://finopenapi.ssafy.io/ssafy", apiKey,
                        "Asia/Seoul", "unused", userKey, escrowAccount, feeAccount, 30), Clock.systemUTC());
        restoreInterruptedProbe(client, userKey, escrowAccount, feeAccount);
        BigInteger escrowBefore = client.inquireAccountBalance(userKey, escrowAccount);
        BigInteger feeBefore = client.inquireAccountBalance(userKey, feeAccount);
        boolean moved = false;
        try {
            String outgoing = client.transfer(userKey, feeAccount, escrowAccount, BigInteger.ONE,
                    "KOMIT-HA-FEE-IN", "KOMIT-HA-FEE-OUT", nextReference());
            moved = true;
            assertThat(outgoing).isNotBlank();
            assertThat(client.inquireAccountBalance(userKey, escrowAccount))
                    .isEqualTo(escrowBefore.subtract(BigInteger.ONE));
            assertThat(client.inquireAccountBalance(userKey, feeAccount))
                    .isEqualTo(feeBefore.add(BigInteger.ONE));
        } finally {
            if (moved) {
                client.transfer(userKey, escrowAccount, feeAccount, BigInteger.ONE,
                        "KOMIT-HA-RESTORE-IN", "KOMIT-HA-RESTORE-OUT", nextReference());
            }
        }
        assertThat(client.inquireAccountBalance(userKey, escrowAccount)).isEqualTo(escrowBefore);
        assertThat(client.inquireAccountBalance(userKey, feeAccount)).isEqualTo(feeBefore);
    }

    private static String nextReference() {
        return REFERENCE_TIME.format(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    }

    private static void restoreInterruptedProbe(SsafyFinanceClient client, String userKey,
            String escrowAccount, String feeAccount) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        long outgoing = client.inquireTransactions(userKey, escrowAccount, today, today, "D", "DESC").stream()
                .filter(item -> "KOMIT-HA-FEE-OUT".equals(item.transactionSummary())).count();
        long restored = client.inquireTransactions(userKey, feeAccount, today, today, "D", "DESC").stream()
                .filter(item -> "KOMIT-HA-RESTORE-OUT".equals(item.transactionSummary())).count();
        for (long pending = outgoing - restored; pending > 0; pending--) {
            client.transfer(userKey, escrowAccount, feeAccount, BigInteger.ONE,
                    "KOMIT-HA-RESTORE-IN", "KOMIT-HA-RESTORE-OUT", nextReference());
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
