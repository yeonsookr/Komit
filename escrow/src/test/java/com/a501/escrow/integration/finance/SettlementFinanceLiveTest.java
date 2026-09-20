package com.a501.escrow.integration.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import com.a501.escrow.config.FinanceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 실제 송금 없이 지급·환불 전 필수 단계인 SSAFY 예금주 조회만 검증한다. */
@EnabledIfEnvironmentVariable(named = "SSAFY_SETTLEMENT_LIVE_TEST", matches = "true")
class SettlementFinanceLiveTest {
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
        assertThat(escrowHolder.accountNo()).isEqualTo(escrowAccount);
        assertThat(escrowHolder.bankCode()).isEqualTo("001");
        assertThat(escrowHolder.userName()).isNotBlank();

        if (feeAccount != null && !feeAccount.isBlank()) {
            FinanceClient.AccountHolder feeHolder = client.inquireAccountHolder(userKey, feeAccount);
            assertThat(feeHolder.accountNo()).isEqualTo(feeAccount).isNotEqualTo(escrowAccount);
            assertThat(feeHolder.userName()).isEqualTo(escrowHolder.userName());
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
