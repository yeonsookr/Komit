package com.a501.escrow.integration.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
                properties("https://finance.example", "test-api-key"),
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
                properties("https://finance.example", "test-api-key"),
                Clock.systemUTC());
        server.expect(requestTo("https://finance.example/api/v1/member/search"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withBadRequest().body("{\"responseCode\":\"E4003\",\"responseMessage\":\"not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThat(client.findUser("missing@example.com")).isEmpty();
        server.verify();
    }

    @Test
    void mapsAccountHolderInquiry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SsafyFinanceClient client = new SsafyFinanceClient(builder, JsonMapper.builder().build(),
                properties("https://finance.example", "test-api-key"), Clock.systemUTC());
        server.expect(requestTo("https://finance.example/api/v1/edu/demandDeposit/inquireDemandDepositAccount"))
                .andExpect(content().json("""
                        {"accountNo":"0011234567890123",
                         "Header":{"apiName":"inquireDemandDepositAccount","userKey":"escrow-key"}}
                        """, false))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},
                         "REC":{"bankCode":"001","accountNo":"0011234567890123","userName":"worker"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.inquireAccountHolder("escrow-key", "0011234567890123"))
                .isEqualTo(new FinanceClient.AccountHolder("001", "0011234567890123", "worker"));
        server.verify();
    }

    @Test
    void mapsDemandDepositBalanceInquiry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SsafyFinanceClient client = new SsafyFinanceClient(builder, JsonMapper.builder().build(),
                properties("https://finance.example", "test-api-key"), Clock.systemUTC());
        server.expect(requestTo("https://finance.example/api/v1/edu/demandDeposit/inquireDemandDepositAccountBalance"))
                .andExpect(content().json("""
                        {"accountNo":"0011234567890123",
                         "Header":{"apiName":"inquireDemandDepositAccountBalance","userKey":"escrow-key"}}
                        """, false))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"accountBalance":"650000"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.inquireAccountBalance("escrow-key", "0011234567890123"))
                .isEqualTo(BigInteger.valueOf(650_000));
        server.verify();
    }

    @Test
    void mapsVirtualAccountCreateTransferVerifySettleAndClose() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SsafyFinanceClient client = new SsafyFinanceClient(builder, JsonMapper.builder().build(),
                properties("https://finance.example", "test-api-key"),
                Clock.fixed(Instant.parse("2026-09-19T12:34:56Z"), ZoneOffset.UTC));

        server.expect(requestTo("https://finance.example/api/v1/edu/account/createVirtualAccount"))
                .andExpect(content().json("""
                        {"mainAccountNo":"0010000000000000","depositorName":"김의뢰",
                         "expectedAmount":"650000","expiryDate":"20261019","autoTransfer":false}
                        """, false))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},
                         "REC":{"virtualAccountId":"va-1","virtualAccountNo":"0011111111111111"}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.createVirtualAccount("escrow-key", "0010000000000000", "김의뢰",
                BigInteger.valueOf(650_000), LocalDate.of(2026, 10, 19), "20260919213456000001"))
                .isEqualTo(new FinanceClient.VirtualAccount("va-1", "0011111111111111"));
        server.verify();
        server.reset();

        server.expect(requestTo("https://finance.example/api/v1/edu/demandDeposit/updateDemandDepositAccountTransfer"))
                .andExpect(content().json("""
                        {"depositAccountNo":"0011111111111111","withdrawalAccountNo":"0012222222222222",
                         "transactionBalance":"650000","depositTransactionSummary":"김의뢰",
                         "withdrawalTransactionSummary":"KOMIT-1"}
                        """, false))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},
                         "REC":[
                           {"accountNo":"0011111111111111","transactionUniqueNo":"tx-2"},
                           {"accountNo":"0012222222222222","transactionUniqueNo":"tx-1"}]}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.transfer("payer-key", "0011111111111111", "0012222222222222",
                BigInteger.valueOf(650_000), "김의뢰", "KOMIT-1", "20260919213456000002"))
                .isEqualTo("tx-1");
        server.verify();
        server.reset();

        server.expect(requestTo("https://finance.example/api/v1/edu/account/inquireVirtualAccountDeposits"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"deposits":[
                          {"depositAmount":"650000","nameMatched":true,"transferred":false}]}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.inquireVirtualAccountDeposits("escrow-key", "va-1"))
                .containsExactly(new FinanceClient.VirtualAccountDeposit(
                        BigInteger.valueOf(650_000), true, false));
        server.verify();
        server.reset();

        server.expect(requestTo("https://finance.example/api/v1/edu/account/settleVirtualAccount"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},
                         "REC":{"settledAmount":"650000","settledAt":"2026-09-19T21:35:00+09:00"}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.settleVirtualAccount("escrow-key", "va-1", "20260919213500000003").settledAmount())
                .isEqualTo(BigInteger.valueOf(650_000));
        server.verify();
        server.reset();

        server.expect(requestTo("https://finance.example/api/v1/edu/account/closeVirtualAccount"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"status":"CLOSED"}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.closeVirtualAccount("escrow-key", "va-1", "20260919213500000004"))
                .isEqualTo("CLOSED");
        server.verify();
    }
    private static FinanceProperties properties(String baseUrl, String apiKey) {
        return new FinanceProperties(true, baseUrl, apiKey, "Asia/Seoul", "product",
                "escrow-user-key", "0010000000000000", "0010000000000001", 30);
    }
}
