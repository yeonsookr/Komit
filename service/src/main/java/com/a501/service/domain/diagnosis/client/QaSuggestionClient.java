package com.a501.service.domain.diagnosis.client;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionResponse;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * AI 서비스에서 확인 문장 후보를 받아 온다.
 *
 * <p><b>공용 {@code RestClient.Builder} 의 타임아웃을 그대로 쓰지 않습니다.</b> 공용 설정은 읽기
 * 5초인데 AI 서비스는 모델 왕복을 최대 20초까지 기다립니다. 5초에 끊으면 AI 가 가려낸
 * {@code 502}·{@code 503} 구분이 이쪽에 닿지 못하고 전부 연결 실패로 뭉개집니다.
 *
 * <p>AI 서비스의 실패는 두 가지뿐입니다. 닿지 못하면 {@code 503}, 닿았는데 계약과 다른 것을
 * 내놓으면 {@code 502} 입니다. 본문의 {@code message} 는 읽지 않습니다 — 사용자에게 보일 문구는
 * 목 명세가 이쪽 것으로 따로 정해 두었습니다.
 */
@Component
public class QaSuggestionClient {
    /** AI 서비스가 모델을 기다리는 상한(20초)보다 길게 둡니다. 먼저 끊으면 실패 구분이 사라집니다. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(25);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final String PATH = "/qa-suggestions";

    private final RestClient client;

    public QaSuggestionClient(
            RestClient.Builder builder, @Value("${app.ai.base-url}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.client = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    /**
     * 요구사항 문장 한 건으로 확인 문장 후보를 만든다.
     *
     * @throws BusinessException {@link DiagnosisErrorCode#AI_RESPONSE_INVALID} 계약과 다른 답이 왔을 때,
     *                           {@link DiagnosisErrorCode#AI_SERVICE_UNAVAILABLE} 닿지 못했을 때
     */
    public List<QaSuggestionResponse> suggest(String requirement) {
        Suggestions body;
        try {
            body = client.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("requirement", requirement))
                    .retrieve()
                    .body(Suggestions.class);
        } catch (RestClientResponseException exception) {
            // 상태 코드가 온 것은 AI 가 판정을 끝냈다는 뜻이다. 그 판정을 그대로 옮긴다.
            throw new BusinessException(
                    exception.getStatusCode().value() == 503
                            ? DiagnosisErrorCode.AI_SERVICE_UNAVAILABLE
                            : DiagnosisErrorCode.AI_RESPONSE_INVALID);
        } catch (RestClientException exception) {
            // 연결 실패·타임아웃·본문 파싱 실패. 답을 못 받았으므로 닿지 못한 것과 같게 다룬다.
            throw new BusinessException(DiagnosisErrorCode.AI_SERVICE_UNAVAILABLE);
        }
        return read(body);
    }

    /** 상태가 {@code 200} 이어도 본문이 계약과 다를 수 있다. 화면에 빈 목록을 내려보내지 않는다. */
    private List<QaSuggestionResponse> read(Suggestions body) {
        if (body == null || body.content() == null || body.content().isEmpty()) {
            throw new BusinessException(DiagnosisErrorCode.AI_RESPONSE_INVALID);
        }
        for (QaSuggestionResponse row : body.content()) {
            if (row == null || row.testCase() == null || row.testCase().isBlank()) {
                throw new BusinessException(DiagnosisErrorCode.AI_RESPONSE_INVALID);
            }
        }
        return body.content();
    }

    /** AI 서비스 응답. 후보 배열 하나만 온다. */
    record Suggestions(List<QaSuggestionResponse> content) {}
}
