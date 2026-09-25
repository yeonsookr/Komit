package com.a501.service.domain.contract.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.ContractDraftInput;
import com.a501.service.domain.contract.dto.ContractDraftMessage;
import com.a501.service.domain.contract.dto.ContractDraftMessage.AuthorRole;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ContractDraftClientTest {
    private HttpServer server;
    private volatile int status;
    private volatile String body;
    private volatile String received;

    @BeforeEach
    void startServer() throws IOException {
        status = 200;
        body = """
                {"draft":{"topic":"인증 오류를 함께 확인합니다","detail":"접근 경로와 재현 절차를 확인합니다."}}""";
        received = null;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/contract-draft", exchange -> {
            received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
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
    @DisplayName("컨설팅 초안을 받고 준비된 입력을 그대로 보낸다")
    void generatesConsultingDraft() {
        ContractDraftClient.ConsultingDraft draft = client().generateConsulting(input());

        assertThat(draft.topic()).isEqualTo("인증 오류를 함께 확인합니다");
        assertThat(draft.detail()).isEqualTo("접근 경로와 재현 절차를 확인합니다.");
        assertThat(received)
                .contains("\"targetType\":\"CONSULTING\"")
                .contains("\"authorRole\":\"CLIENT\"")
                .contains("\"content\":\"관리자 화면에 들어갈 수 없습니다.\"");
    }

    @Test
    @DisplayName("detail은 null일 수 있다")
    void acceptsNullDetail() {
        body = """
                {"draft":{"topic":"인증 오류를 확인합니다","detail":null}}""";

        assertThat(client().generateConsulting(input()).detail()).isNull();
    }

    @Test
    @DisplayName("topic은 정확히 255자까지 받을 수 있다")
    void acceptsMaximumTopicLength() {
        body = "{\"draft\":{\"topic\":\"%s\",\"detail\":null}}".formatted("가".repeat(255));

        assertThat(client().generateConsulting(input()).topic()).hasSize(255);
    }

    @Test
    @DisplayName("초안이 없거나 필드가 다르면 502다")
    void rejectsMissingOrMalformedDraft() {
        for (String wrong : List.of(
                "{\"draft\":null}",
                "{\"draft\":{\"topic\":\"주제\"}}",
                "{\"draft\":{\"topic\":\" \",\"detail\":null}}",
                "{\"draft\":{\"topic\":\"주제\",\"detail\":\" \"}}",
                "{\"draft\":{\"topic\":\"%s\",\"detail\":null}}".formatted("가".repeat(256)),
                "{\"draft\":{\"topic\":\"주제\",\"detail\":null,\"price\":1000}}")) {
            body = wrong;
            assertThatThrownBy(() -> client().generateConsulting(input()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.AI_RESPONSE_INVALID);
        }
    }

    @Test
    @DisplayName("AI Worker의 502와 503을 계약 도메인 오류로 옮긴다")
    void mapsWorkerFailures() {
        status = 502;
        body = "{\"code\":\"AI_RESPONSE_INVALID\",\"message\":\"형식 위반\"}";
        assertThatThrownBy(() -> client().generateConsulting(input()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.AI_RESPONSE_INVALID);

        status = 503;
        body = "{\"code\":\"AI_SERVICE_UNAVAILABLE\",\"message\":\"연결 실패\"}";
        assertThatThrownBy(() -> client().generateConsulting(input()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("백엔드가 계약과 다른 요청을 보냈다는 응답은 500이다")
    void requestInvalidMeansInternalError() {
        status = 422;
        body = "{\"code\":\"AI_REQUEST_INVALID\",\"message\":\"요청 형식 위반\"}";

        assertThatThrownBy(() -> client().generateConsulting(input()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("컨설팅 호출에 개발 계약 입력을 넘기지 않는다")
    void rejectsDevelopmentInputBeforeCallingWorker() {
        ContractDraftInput development = new ContractDraftInput("DEVELOPMENT", input().messages());

        assertThatThrownBy(() -> client().generateConsulting(development))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INTERNAL_ERROR);
        assertThat(received).isNull();
    }

    private ContractDraftClient client() {
        return new ContractDraftClient(
                RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5));
    }

    private ContractDraftInput input() {
        return new ContractDraftInput(
                "CONSULTING",
                List.of(new ContractDraftMessage(
                        "inquiry-room-1",
                        AuthorRole.CLIENT,
                        Instant.parse("2026-09-26T01:00:00Z"),
                        "관리자 화면에 들어갈 수 없습니다.")));
    }
}
