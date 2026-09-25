package com.a501.service.domain.consulting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.domain.consulting.client.ConsultingSummaryClient;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 전사가 남으면 뒤에서 요약이 만들어지는지 진짜 Tomcat 과 PostgreSQL 로 본다.
 *
 * <p><b>단위 시험이 못 보는 자리다.</b> 요약은 {@code @Async} 와 커밋 뒤 리스너가 맞물려야 돈다.
 * {@code @EnableAsync} 가 빠지면 노트북이 요약을 기다리다 제 한도를 넘기고, 이벤트가 트랜잭션 밖에서
 * 나가면 리스너가 조용히 무시해 요약이 영영 없다. 어느 쪽도 오류를 내지 않는다.
 *
 * <p>AI 만 가짜로 둔다. 일부러 느리게 답해 요청이 요약을 기다리지 않는지 본다.
 */
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "app.transcriber.token=" + SessionSummaryIntegrationTest.TOKEN)
class SessionSummaryIntegrationTest {
    static final String TOKEN = "integration-transcriber-token";
    private static final long AI_DELAY_MILLIS = 5_000;
    private static final String RESULT = "{\"utterances\": [{\"utteranceIndex\": 0, \"speaker\": \"CLIENT\", "
            + "\"startMs\": 0, \"endMs\": 1200, \"rawText\": \"로그인이 안 돼요\"}]}";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    @LocalServerPort int port;

    @Autowired ConsultingSessionRepository sessionRepository;
    @MockitoBean ConsultingSummaryClient summaryClient;

    private final HttpClient http = HttpClient.newHttpClient();
    private Integer sessionId;

    @BeforeEach
    void 끝난_세션과_느린_AI() {
        ConsultingSession session = ConsultingSession.of(1, 50_000, 30);
        session.completeConsulting(LocalDateTime.now());
        sessionId = sessionRepository.saveAndFlush(session).getId();

        when(summaryClient.summarize(any())).thenAnswer(call -> {
            Thread.sleep(AI_DELAY_MILLIS);
            return new ConsultingSummaryClient.AiSummary(
                    List.of(new ConsultingSummaryClient.AiItem("로그인 오류", null)), List.of(), List.of(), List.of());
        });
    }

    @AfterEach
    void 만든_세션을_지운다() {
        sessionRepository.deleteById(sessionId);
    }

    @Test
    void 전사를_올리면_기다리지_않고_끝나고_요약은_뒤에서_남는다() throws Exception {
        long started = System.nanoTime();
        HttpResponse<String> response = postResult();
        long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(response.statusCode()).isEqualTo(204);
        // AI 가 5초 걸리므로, 요청 안에서 요약을 기다렸다면 5초를 넘는다. 첫 요청이 느려도 넉넉하다.
        assertThat(tookMillis).isLessThan(AI_DELAY_MILLIS);

        awaitUntil(() -> summary() != null);
        assertThat(summary()).contains("로그인 오류").contains("discussed");
    }

    @Test
    void 같은_결과가_또_와도_요약은_한_번만_만든다() throws Exception {
        postResult();
        awaitUntil(() -> summary() != null);

        assertThat(postResult().statusCode()).isEqualTo(204);
        Thread.sleep(500);

        verify(summaryClient, times(1)).summarize(any());
    }

    private HttpResponse<String> postResult() throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/transcription-jobs/" + sessionId + "/result"))
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(RESULT))
                        .timeout(Duration.ofSeconds(10))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String summary() {
        return sessionRepository.findById(sessionId).orElseThrow().getSummary();
    }

    private void awaitUntil(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("기다리던 상태가 되지 않았다");
    }
}
