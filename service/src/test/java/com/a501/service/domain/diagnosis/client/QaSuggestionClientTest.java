package com.a501.service.domain.diagnosis.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * AI 서비스 호출의 계약 테스트.
 *
 * <p><b>{@code MockRestServiceServer} 를 쓰지 않는다.</b> 그 방식은 빌더에 목 요청 팩토리를 꽂는데,
 * 이 클라이언트는 자기 타임아웃을 걸려고 요청 팩토리를 직접 만들어 그것을 덮어쓴다. 실제 HTTP 를
 * 태워야 상태 코드·본문·타임아웃 처리가 함께 검증된다.
 */
class QaSuggestionClientTest {
    private static final String GOOD = """
            {"content":[{"testCase":"첫째"},{"testCase":"둘째"},{"testCase":"셋째"}]}""";

    private HttpServer server;
    /** 서버 핸들러 스레드가 쓰고 테스트 스레드가 읽는다. 가시성을 언어가 보장하게 둔다. */
    private volatile int status;

    private volatile String body;
    private volatile long delayMillis;
    /** 가짜 서버가 받은 요청 본문. 무엇을 보냈는지 검증하려고 담아 둔다. */
    private volatile String received;
    private QaSuggestionClient client;
    /** 500 은 `handleBusiness` 가 로그를 남기지 않는다. 여기서 남기는지 직접 본다. */
    private final Logger clientLogger = (Logger) LoggerFactory.getLogger(QaSuggestionClient.class);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void startServer() throws IOException {
        logs.list.clear();
        logs.start();
        clientLogger.addAppender(logs);
        // 프레임워크 기본 수명주기에 기대지 않고 여기서 되돌린다.
        status = 200;
        body = GOOD;
        delayMillis = 0;
        received = null;
        client = null;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/qa-suggestions", exchange -> {
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
        clientLogger.detachAppender(logs);
        server.stop(0);
    }

    @Test
    @DisplayName("계약에 맞는 후보 세 건은 그대로 돌려준다")
    void returnsThreeSuggestions() {
        assertThat(client().suggest("관리자 화면에 인증이 없다"))
                .extracting("testCase")
                .containsExactly("첫째", "둘째", "셋째");
    }

    @Test
    @DisplayName("요구사항 문장을 requirement 칸에 담아 보낸다")
    void sendsRequirementField() {
        client().suggest("관리자 화면에 인증이 없다");

        assertThat(received).isEqualTo("{\"requirement\":\"관리자 화면에 인증이 없다\"}");
    }

    @Test
    @DisplayName("줄바꿈이 든 문장도 한 칸에 그대로 담는다")
    void keepsLineBreaksInsideOneField() {
        client().suggest("제목" + System.lineSeparator() + "내용");

        // JSON 은 줄바꿈을 두 글자 이스케이프로 싣는다. 실제 줄바꿈이 본문에 나가면 그 자리에서 깨진다.
        assertThat(received).doesNotContain("\n").contains("제목").contains("내용");
    }

    @Test
    @DisplayName("후보 수가 세 건이 아니면 502다")
    void rejectsWrongCandidateCount() {
        for (String wrong : List.of(
                """
                {"content":[{"testCase":"하나"}]}""",
                """
                {"content":[{"testCase":"하나"},{"testCase":"둘"},{"testCase":"셋"},{"testCase":"넷"}]}""",
                """
                {"content":[]}""",
                """
                {"content":null}""")) {
            body = wrong;
            assertThatThrownBy(() -> client().suggest("요구사항"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.AI_RESPONSE_INVALID);
        }
    }

    @Test
    @DisplayName("두 건만 와도 502다. 지적의 발단이 이 경우였다")
    void rejectsTwoCandidates() {
        body = """
                {"content":[{"testCase":"하나"},{"testCase":"둘"}]}""";

        assertThatThrownBy(() -> client().suggest("요구사항"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.AI_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("본문이 아예 비어 있으면 502다")
    void emptyBodyIsResponseInvalid() {
        body = "";

        assertThatThrownBy(() -> client().suggest("요구사항"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.AI_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("후보 자리에 null 이 오거나 testCase 가 null 이면 502다")
    void rejectsNullRowOrNullTestCase() {
        for (String wrong : List.of(
                """
                {"content":[null,{"testCase":"둘째"},{"testCase":"셋째"}]}""",
                """
                {"content":[{"testCase":null},{"testCase":"둘째"},{"testCase":"셋째"}]}""")) {
            body = wrong;
            assertThatThrownBy(() -> client().suggest("요구사항"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.AI_RESPONSE_INVALID);
        }
    }

    @Test
    @DisplayName("빈 문장이나 500자를 넘는 문장은 502다")
    void rejectsBlankOrTooLongSentence() {
        for (String sentence : List.of("   ", "가".repeat(501))) {
            body = """
                    {"content":[{"testCase":"%s"},{"testCase":"둘째"},{"testCase":"셋째"}]}"""
                    .formatted(sentence);
            assertThatThrownBy(() -> client().suggest("요구사항"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.AI_RESPONSE_INVALID);
        }
    }

    @Test
    @DisplayName("정확히 500자는 통과한다. 상한을 한 칸 좁히면 여기서 걸린다")
    void acceptsExactlyMaxLength() {
        body = """
                {"content":[{"testCase":"%s"},{"testCase":"둘째"},{"testCase":"셋째"}]}"""
                .formatted("가".repeat(500));

        assertThat(client().suggest("요구사항")).hasSize(3);
    }

    @Test
    @DisplayName("200이어도 본문을 읽지 못하면 502다")
    void unreadableBodyIsResponseInvalid() {
        body = """
                {"content":"세 건"}""";

        assertThatThrownBy(() -> client().suggest("요구사항"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.AI_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("AI 가 못 닿았다고 하면 503으로 옮긴다")
    void unavailableCodeBecomesServiceUnavailable() {
        status = 503;
        body = """
                {"code":"AI_SERVICE_UNAVAILABLE","message":"AI 서비스에 연결하지 못했습니다."}""";

        assertThatThrownBy(() -> client().suggest("요구사항"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("AI 가 결과를 못 만들었다고 하면 502로 옮긴다")
    void responseInvalidCodeStaysResponseInvalid() {
        status = 502;
        body = """
                {"code":"AI_RESPONSE_INVALID","message":"AI가 계약에 맞는 결과를 만들지 못했습니다."}""";

        assertThatThrownBy(() -> client().suggest("요구사항"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.AI_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("우리가 계약과 다르게 보냈다는 답은 500이다. 재시도를 권하지 않는다")
    void requestInvalidCodeBecomesInternalError() {
        status = 422;
        body = """
                {"code":"AI_REQUEST_INVALID","message":"요구사항 문장 한 건이 필요합니다"}""";

        assertThatThrownBy(() -> client().suggest("   "))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INTERNAL_ERROR);
        assertThat(logs.list).anyMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    @DisplayName("계약 형식이지만 모르는 code 면 상태 코드로 가른다. 4xx 는 우리 잘못이다")
    void unknownCodeOn4xxIsOurMistake() {
        status = 404;
        body = """
                {"code":"SOMETHING_ELSE","message":"요청하신 경로가 없습니다."}""";

        assertThatThrownBy(() -> client().suggest("요구사항"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INTERNAL_ERROR);
        assertThat(logs.list).anyMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    @DisplayName("계약 형식이 아닌 본문이 4xx 로 와도 우리 잘못이고 ERROR 로 남는다")
    void brokenBodyOn4xxIsLoggedAsError() {
        status = 404;
        body = "<html>proxy not found</html>";

        assertThatThrownBy(() -> client().suggest("요구사항"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INTERNAL_ERROR);
        // 본문을 못 읽어도 500 이 조용히 나가면 안 된다. 원인을 볼 곳이 이 한 줄뿐이다.
        assertThat(logs.list).anyMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    @DisplayName("code 칸이 없는 실패 본문도 상태 코드로 가른다")
    void failureWithoutCodeFallsBackToStatus() {
        status = 503;
        body = """
                {"message":"게이트웨이가 만든 본문"}""";

        assertThatThrownBy(() -> client().suggest("요구사항"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("본문 없는 503 도 503으로 옮긴다. 프록시가 그렇게 답한다")
    void bareServiceUnavailableIsServiceUnavailable() {
        status = 503;
        body = "";

        assertThatThrownBy(() -> client().suggest("요구사항"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("계약 형식이 아닌 실패 본문은 상태 코드로 가른다")
    void unknownFailureBodyFallsBackToStatus() {
        status = 500;
        body = "<html>proxy error</html>";

        assertThatThrownBy(() -> client().suggest("요구사항"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.AI_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("닿지 못하면 503이다")
    void unreachableIsServiceUnavailable() {
        server.stop(0);

        assertThatThrownBy(() -> client().suggest("요구사항"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("읽기 한도를 넘기면 503이다")
    void readTimeoutIsServiceUnavailable() {
        delayMillis = 400;

        assertThatThrownBy(() -> client(Duration.ofMillis(100)).suggest("요구사항"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("공용 빌더를 고치지 않는다")
    void neverMutatesSharedBuilder() {
        RestClient.Builder shared = RestClient.builder();

        new QaSuggestionClient(shared, baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(1));

        // 사본을 쓰지 않으면 이 빌더로 만든 다음 클라이언트가 위 주소와 한도를 물려받는다.
        assertThatThrownBy(() -> shared.build().get().uri("/qa-suggestions").retrieve().body(String.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 시험마다 한 번만 만든다.
     *
     * <p>생성자가 {@code HttpClient} 를 새로 세우는데 밖에서 닫을 길이 없다. 반복문 안에서 매번
     * 만들면 시험 하나에 여남은 개가 쌓인다.
     */
    private QaSuggestionClient client() {
        if (client == null) {
            client = client(Duration.ofSeconds(5));
        }
        return client;
    }

    private QaSuggestionClient client(Duration readTimeout) {
        return new QaSuggestionClient(
                RestClient.builder(), baseUrl(), Duration.ofSeconds(1), readTimeout);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
