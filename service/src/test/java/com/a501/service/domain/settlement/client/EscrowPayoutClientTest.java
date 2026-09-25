package com.a501.service.domain.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.settlement.SettlementErrorCode;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.LedgerEntry;
import com.a501.service.domain.settlement.client.EscrowPayoutClient.PayoutRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Escrow 지급 호출의 계약 테스트.
 *
 * <p>이 클라이언트도 자기 타임아웃을 걸려고 요청 팩토리를 직접 만든다. {@code MockRestServiceServer} 로는
 * 대역이 덮어써지므로 실제 HTTP 를 태운다. (BACKEND_CONVENTIONS 19장 바깥 클라이언트 테스트)
 */
class EscrowPayoutClientTest {
    private static final String OK_HEADER = "{\"responseCode\":\"H0000\",\"responseMessage\":\"정상처리 되었습니다.\"}";
    private static final PayoutRequest REQUEST = new PayoutRequest(
            301, "SETTLEMENT:CONSULTING-120", BigInteger.valueOf(50_000),
            "001", "0011234567890123", "komit-9", "escrow:release:consulting-contract-120");

    private final ObjectMapper json = new ObjectMapper();
    /** 경로별로 돌려줄 상태와 본문. 서버 스레드가 읽고 테스트 스레드가 쓴다. */
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Map<String, String> received = new ConcurrentHashMap<>();
    private volatile long delayMillis;
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        delayMillis = 0;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/escrow/", exchange -> {
            String name = exchange.getRequestURI().getPath().substring("/api/v1/escrow/".length());
            received.put(name, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] payload = bodies.getOrDefault(name, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statuses.getOrDefault(name, 200), payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("지급 요청에 목 명세의 본문과 공통 Header 를 싣는다")
    void sendsReleaseContract() {
        respond("releaseEscrow", 200, success("{\"transactionId\":1001,\"status\":\"COMPLETED\"}"));

        assertThat(client().release(REQUEST)).isEqualTo("COMPLETED");

        JsonNode sent = json.readTree(received.get("releaseEscrow"));
        assertThat(sent.path("escrowId").asLong()).isEqualTo(301);
        assertThat(sent.path("referenceId").asString()).isEqualTo("SETTLEMENT:CONSULTING-120");
        assertThat(sent.path("amount").asLong()).isEqualTo(50_000);
        assertThat(sent.path("recipientBankCode").asString()).isEqualTo("001");
        assertThat(sent.path("recipientAccountNo").asString()).isEqualTo("0011234567890123");
        assertThat(sent.path("recipientName").asString()).isEqualTo("komit-9");
        JsonNode header = sent.path("Header");
        assertThat(header.path("apiName").asString()).isEqualTo("releaseEscrow");
        assertThat(header.path("apiServiceCode").asString()).isEqualTo("releaseEscrow");
        assertThat(header.path("idempotencyKey").asString()).isEqualTo("escrow:release:consulting-contract-120");
        assertThat(header.path("clientApiKey").asString()).isEqualTo("test-client-key");
        assertThat(header.path("institutionTransactionUniqueNo").asString()).matches("\\d{20}");
        assertThat(header.has("userKey")).isFalse();
    }

    @Test
    @DisplayName("계약 id 로 목록을 찾고 상세에서 거래 목록을 읽는다")
    void readsLedgerThroughListAndDetail() {
        respond("inquireEscrowList", 200, success(
                "{\"totalCount\":1,\"list\":[{\"escrowId\":301,\"contractId\":\"120\",\"status\":\"ACTIVE\",\"balance\":50000,\"isHeld\":false}]}"));
        respond("inquireEscrow", 200, success("""
                {"escrowId":301,"contractId":"120","status":"ACTIVE","isHeld":true,"depositedAmount":50000,"transactions":[
                  {"transactionId":1,"transactionType":"DEPOSIT","amount":50000,"status":"COMPLETED","referenceId":"INITIAL"},
                  {"transactionId":2,"transactionType":"RELEASE","amount":49500,"status":"UNKNOWN","referenceId":"SETTLEMENT:CONSULTING-120"}]}"""));

        var ledger = client().findByContract("120").orElseThrow();

        assertThat(ledger.escrowId()).isEqualTo(301);
        assertThat(ledger.status()).isEqualTo("ACTIVE");
        assertThat(ledger.held()).isTrue();
        assertThat(ledger.depositedAmount()).isEqualTo(BigInteger.valueOf(50_000));
        assertThat(ledger.transactions()).containsExactly(
                new LedgerEntry("DEPOSIT", "COMPLETED", "INITIAL"),
                new LedgerEntry("RELEASE", "UNKNOWN", "SETTLEMENT:CONSULTING-120"));
        assertThat(json.readTree(received.get("inquireEscrow")).path("escrowId").asLong()).isEqualTo(301);
        assertThat(json.readTree(received.get("inquireEscrowList")).path("Header").has("idempotencyKey")).isFalse();
    }

    @Test
    @DisplayName("예치가 없으면 비어 있고 상세는 묻지 않는다")
    void emptyWhenNotDeposited() {
        respond("inquireEscrowList", 200, success("{\"totalCount\":0,\"list\":[]}"));

        assertThat(client().findByContract("120")).isEmpty();
        assertThat(received).doesNotContainKey("inquireEscrow");
    }

    @Test
    @DisplayName("503 과 Q1000 은 결과 불명이다. 실패로 확정하지 않는다")
    void unknownResults() {
        respond("releaseEscrow", 503, failure("Q1000"));
        assertCode(SettlementErrorCode.ESCROW_UNAVAILABLE);

        respond("releaseEscrow", 400, failure("Q1000"));
        assertCode(SettlementErrorCode.ESCROW_UNAVAILABLE);

        respond("releaseEscrow", 200, "{\"Header\":{\"responseCode\":\"Q1000\"}}");
        assertCode(SettlementErrorCode.ESCROW_UNAVAILABLE);
    }

    @Test
    @DisplayName("본문을 읽을 수 없는 실패도 결과 불명으로 다룬다")
    void unreadableFailureIsUnknown() {
        respond("releaseEscrow", 502, "<html>bad gateway</html>");

        assertCode(SettlementErrorCode.ESCROW_UNAVAILABLE);
    }

    @Test
    @DisplayName("동결은 E5007 로 가른다")
    void heldEscrow() {
        respond("releaseEscrow", 400, failure("E5007"));

        assertCode(SettlementErrorCode.ESCROW_HELD);
    }

    @Test
    @DisplayName("예금주 불일치 같은 거절은 자동으로 다시 부르지 않을 갈래다")
    void rejectedPayout() {
        for (String code : List.of("E5016", "E5009", "E5008", "E5003", "H1008")) {
            respond("releaseEscrow", 400, failure(code));
            assertCode(SettlementErrorCode.PAYOUT_REJECTED);
        }
    }

    @Test
    @DisplayName("성공 코드인데 REC 가 없으면 결과 불명이다")
    void successWithoutRecord() {
        respond("releaseEscrow", 200, "{\"Header\":" + OK_HEADER + "}");

        assertCode(SettlementErrorCode.ESCROW_UNAVAILABLE);
    }

    @Test
    @DisplayName("읽기 한도를 넘기면 결과 불명이다")
    void readTimeoutIsUnknown() {
        respond("releaseEscrow", 200, success("{\"status\":\"COMPLETED\"}"));
        delayMillis = 800;

        assertThatThrownBy(() -> client(Duration.ofMillis(200)).release(REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", SettlementErrorCode.ESCROW_UNAVAILABLE);
    }

    @Test
    @DisplayName("계좌번호는 요청 객체의 문자열 표현에 나오지 않는다")
    void requestToStringHidesAccount() {
        assertThat(REQUEST.toString()).doesNotContain("0011234567890123").doesNotContain("komit-9");
    }

    private void assertCode(SettlementErrorCode expected) {
        assertThatThrownBy(() -> client().release(REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    private void respond(String name, int status, String body) {
        statuses.put(name, status);
        bodies.put(name, body);
    }

    private static String success(String record) {
        return "{\"Header\":" + OK_HEADER + ",\"REC\":" + record + "}";
    }

    private static String failure(String code) {
        return "{\"Header\":{\"responseCode\":\"" + code + "\",\"responseMessage\":\"실패\"}}";
    }

    private EscrowPayoutClient client() {
        return client(Duration.ofSeconds(5));
    }

    private EscrowPayoutClient client(Duration readTimeout) {
        return new EscrowPayoutClient(
                RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-client-key",
                Duration.ofSeconds(1),
                readTimeout);
    }
}
