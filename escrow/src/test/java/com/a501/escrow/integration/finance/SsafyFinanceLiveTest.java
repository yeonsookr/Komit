package com.a501.escrow.integration.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;

import com.a501.escrow.config.FinanceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 승인된 SSAFY 테스트 환경에서만 명시적으로 실행하는 읽기 전용 연결 검사입니다. */
@EnabledIfEnvironmentVariable(named = "SSAFY_LIVE_TEST", matches = "true")
class SsafyFinanceLiveTest {
    @Test
    void readsProvisionedMemberAccountAndTransactions() {
        String apiKey = required("SSAFY_FINANCE_API_KEY");
        String userId = required("SSAFY_LIVE_TEST_USER_ID");
        SsafyFinanceClient client = new SsafyFinanceClient(RestClient.builder(), JsonMapper.builder().build(),
                new FinanceProperties(true, "https://finopenapi.ssafy.io/ssafy", apiKey,
                        "Asia/Seoul", "001-1-231ecb61d2554c", "unused",
                        "0010000000000000", 30), Clock.systemUTC());

        FinanceClient.UserRegistration user = client.findUser(userId).orElseThrow();
        FinanceClient.DemandDepositAccount account = client.findDemandDepositAccounts(user.userKey()).stream()
                .filter(candidate -> "Komit 데모 입출금".equals(candidate.accountName()))
                .findFirst().orElseThrow();
        var transactions = client.inquireTransactions(user.userKey(), account.accountNo(),
                LocalDate.now().minusDays(1), LocalDate.now(), "A", "DESC");

        assertThat(user.userId()).isEqualTo(userId);
        assertThat(account.bankCode()).isEqualTo("001");
        assertThat(account.currency()).isEqualTo("KRW");
        assertThat(transactions).anyMatch(transaction -> "50000000".equals(transaction.transactionBalance()));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
