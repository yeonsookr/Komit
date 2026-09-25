package com.a501.service.domain.contract.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.contract.ContractErrorCode;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Escrow 응답을 실제 JSON 으로 읽는지 봅니다. (BACKEND_CONVENTIONS 19 바깥 클라이언트 테스트)
 *
 * <p>응답을 Jackson 2 타입으로 달라고 하면 컴파일은 되지만 Spring Boot 4 의 변환기(Jackson 3)가 읽지 못해
 * 실행 중에 500 이 납니다. 서비스 테스트는 이 클라이언트를 가짜로 바꾸므로 여기서만 잡힙니다.
 * 응답 본문은 목 Escrow({@code komit.site/mock/escrow})가 실제로 돌려준 모양입니다.
 */
class EscrowDepositClientTest {
    private static final String BASE = "http://localhost:8081";

    @Test
    void findsDepositByContractThenReadsItsBalance() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EscrowDepositClient client = new EscrowDepositClient(builder, BASE, "test-key");
        server.expect(requestTo(BASE + "/api/v1/escrow/inquireEscrowList"))
                .andExpect(jsonPath("$.contractIds[0]").value("130"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"totalCount":1,"list":[
                          {"escrowId":1000130,"contractId":"130","status":"ACTIVE","balance":650000,"isHeld":false}]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/v1/escrow/inquireEscrow"))
                .andExpect(jsonPath("$.escrowId").value(1000130))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"escrowId":1000130,"contractId":"130","status":"ACTIVE",
                          "depositedAmount":650000,"releasedAmount":350000,"feeAmount":0,"refundedAmount":0,"balance":300000}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findByContract("130")).contains(new EscrowDepositClient.Deposit(
                1000130L, "130", "ACTIVE", BigInteger.valueOf(650000), BigInteger.valueOf(300000)));
        server.verify();
    }

    @Test
    void emptyListMeansNoDeposit() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EscrowDepositClient client = new EscrowDepositClient(builder, BASE, "test-key");
        server.expect(requestTo(BASE + "/api/v1/escrow/inquireEscrowList"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"totalCount":0,"list":[]}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findByContract("131")).isEmpty();
        server.verify();
    }

    @Test
    void createsDepositAndReadsTheRecord() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EscrowDepositClient client = new EscrowDepositClient(builder, BASE, "test-key");
        server.expect(requestTo(BASE + "/api/v1/escrow/createEscrow"))
                .andExpect(jsonPath("$.Header.userKey").value("user-key"))
                .andExpect(jsonPath("$.Header.idempotencyKey").value("dep-130"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"escrowId":1000130,"status":"DEPOSIT_PENDING",
                          "depositedAmount":650000,"balance":650000}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.create("130", "CONSULTING", "김미미", BigInteger.valueOf(650000),
                "user-key", "001", "0015337596722826", "dep-130"))
                .isEqualTo(new EscrowDepositClient.Deposit(
                        1000130L, "130", "DEPOSIT_PENDING", BigInteger.valueOf(650000), BigInteger.valueOf(650000)));
        server.verify();
    }

    @Test
    void failedHeaderIsEscrowUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EscrowDepositClient client = new EscrowDepositClient(builder, BASE, "test-key");
        server.expect(requestTo(BASE + "/api/v1/escrow/inquireEscrowList"))
                .andRespond(withBadRequest().body("""
                        {"Header":{"responseCode":"E5004","responseMessage":"에스크로를 찾을 수 없습니다."}}
                        """).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findByContract("130"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ContractErrorCode.ESCROW_UNAVAILABLE);
        server.verify();
    }
}
