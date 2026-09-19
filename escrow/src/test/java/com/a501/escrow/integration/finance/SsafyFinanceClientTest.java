package com.a501.escrow.integration.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.a501.escrow.config.FinanceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class SsafyFinanceClientTest {
    @Test
    void sendsSsafyHeaderAndSerializesMoneyAsString() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SsafyFinanceClient client = new SsafyFinanceClient(builder, JsonMapper.builder().build(),
                new FinanceProperties(true, "https://finance.example", "test-api-key", "Asia/Seoul", "product"),
                Clock.fixed(Instant.parse("2026-09-19T12:34:56Z"), ZoneOffset.UTC));
        server.expect(requestTo("https://finance.example/api/v1/edu/demandDeposit/updateDemandDepositAccountDeposit"))
                .andExpect(content().json("""
                        {
                          "Header": {
                            "apiName": "updateDemandDepositAccountDeposit",
                            "transmissionDate": "20260919",
                            "transmissionTime": "213456",
                            "institutionCode": "00100",
                            "fintechAppNo": "001",
                            "apiServiceCode": "updateDemandDepositAccountDeposit",
                            "apiKey": "test-api-key",
                            "userKey": "user-key"
                          },
                          "accountNo": "0011234567890123",
                          "transactionBalance": "50000000",
                          "transactionSummary": "seed-reference"
                        }
                        """, false))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},
                         "REC":{"transactionUniqueNo":"123","transactionDate":"20260919"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.depositSeedMoney("user-key", "0011234567890123",
                BigInteger.valueOf(50_000_000L), "seed-reference")).isEqualTo("123");
        server.verify();
    }

    @Test
    void mapsMemberNotFoundToEmptyResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SsafyFinanceClient client = new SsafyFinanceClient(builder, JsonMapper.builder().build(),
                new FinanceProperties(true, "https://finance.example", "test-api-key", "Asia/Seoul", "product"),
                Clock.systemUTC());
        server.expect(requestTo("https://finance.example/api/v1/member/search"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withBadRequest().body("{\"responseCode\":\"E4003\",\"responseMessage\":\"not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThat(client.findUser("missing@example.com")).isEmpty();
        server.verify();
    }
}
