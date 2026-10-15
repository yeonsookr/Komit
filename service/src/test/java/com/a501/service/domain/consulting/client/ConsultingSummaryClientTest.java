package com.a501.service.domain.consulting.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a501.service.domain.consulting.dto.Speaker;
import com.a501.service.domain.consulting.dto.SummaryContent;
import com.a501.service.domain.consulting.dto.request.TranscriptResultRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 요약 호출의 계약 테스트. 자기 요청 팩토리로 타임아웃을 걸어 {@code MockRestServiceServer} 가
 * 붙지 않으므로 실제 HTTP 로 돈다. (BACKEND_CONVENTIONS 19, {@code QaSuggestionClientTest})
 */
class ConsultingSummaryClientTest {
    /** AI 가 실제로 돌려주는 모양. 항목마다 근거 순번과 검토 표시가 붙는다. */
    private static final String GOOD = """
            {"discussed":[{"title":"로그인 오류","detail":null,"evidenceUtteranceIndexes":[0],"reviewRequired":false}],
             "decided":[],
             "nextActions":[{"title":"로그 수집","detail":"금요일까지","evidenceUtteranceIndexes":[],"reviewRequired":true}],
             "changes":[{"kind":"ADD","issueId":null,"title":"로그인 실패 알림","detail":null,
                         "evidenceUtteranceIndexes":[0],"reviewRequired":false}]}""";

    private static final List<TranscriptResultRequest.Utterance> UTTERANCES = List.of(
            new TranscriptResultRequest.Utterance(0, Speaker.CLIENT, 0, 1200, "로그인이 안 돼요"));

    private final ObjectMapper json = JsonMapper.builder().build();
    private HttpServer server;
    private volatile int status;
    private volatile String body;
    private volatile long delayMillis;
    private volatile String received;

    @BeforeEach
    void startServer() throws IOException {
        status = 200;
        body = GOOD;
        delayMillis = 0;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/consulting-summary", exchange -> {
            received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
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
    void 발화를_보내고_요구사항은_아직_비워_보낸다() {
        client().summarize(UTTERANCES);

        assertThat(json.readTree(received)).isEqualTo(json.readTree("""
                {"utterances":[{"utteranceIndex":0,"speaker":"CLIENT","startMs":0,"endMs":1200,
                                "rawText":"로그인이 안 돼요"}],
                 "requirements":[]}"""));
    }

    @Test
    void 근거_순번과_검토_표시는_읽지_않는다() {
        ConsultingSummaryClient.AiSummary summary = client().summarize(UTTERANCES);

        assertThat(summary.discussed()).containsExactly(new ConsultingSummaryClient.AiItem("로그인 오류", null));
        assertThat(summary.nextActions()).containsExactly(new ConsultingSummaryClient.AiItem("로그 수집", "금요일까지"));
        assertThat(summary.changes()).containsExactly(
                new ConsultingSummaryClient.AiChange(SummaryContent.Kind.ADD, null, "로그인 실패 알림", null));
    }

    @Test
    void 배열이_빠지면_계약과_다른_답으로_본다() {
        body = "{\"discussed\":[],\"decided\":[],\"nextActions\":[]}";

        assertReason(SummaryFailedException.Reason.RESPONSE_INVALID);
    }

    @Test
    void 실패는_상대가_준_code_로_가른다() {
        status = 503;
        body = "{\"code\":\"AI_SERVICE_UNAVAILABLE\",\"message\":\"x\"}";
        assertReason(SummaryFailedException.Reason.UNAVAILABLE);

        status = 502;
        body = "{\"code\":\"AI_RESPONSE_INVALID\",\"message\":\"x\"}";
        assertReason(SummaryFailedException.Reason.RESPONSE_INVALID);

        status = 422;
        body = "{\"code\":\"AI_REQUEST_INVALID\",\"message\":\"x\"}";
        assertReason(SummaryFailedException.Reason.REQUEST_INVALID);
    }

    @Test
    void 본문이_계약_형식이_아니면_상태_코드로_가른다() {
        status = 503;
        body = "<html>bad gateway</html>";
        assertReason(SummaryFailedException.Reason.UNAVAILABLE);

        status = 500;
        assertReason(SummaryFailedException.Reason.RESPONSE_INVALID);
    }

    @Test
    void 읽기_한도를_넘기면_닿지_못한_것으로_본다() {
        delayMillis = 1_000;

        assertThatThrownBy(() -> client(Duration.ofMillis(200)).summarize(UTTERANCES))
                .isInstanceOf(SummaryFailedException.class)
                .extracting("reason")
                .isEqualTo(SummaryFailedException.Reason.UNAVAILABLE);
    }

    private void assertReason(SummaryFailedException.Reason reason) {
        assertThatThrownBy(() -> client().summarize(UTTERANCES))
                .isInstanceOf(SummaryFailedException.class)
                .extracting("reason")
                .isEqualTo(reason);
    }

    private ConsultingSummaryClient client() {
        return client(Duration.ofSeconds(5));
    }

    private ConsultingSummaryClient client(Duration readTimeout) {
        return new ConsultingSummaryClient(
                RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1),
                readTimeout);
    }
}
