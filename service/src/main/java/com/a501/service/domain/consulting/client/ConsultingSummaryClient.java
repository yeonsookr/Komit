package com.a501.service.domain.consulting.client;

import com.a501.service.domain.consulting.dto.SummaryContent;
import com.a501.service.domain.consulting.dto.request.TranscriptResultRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * AI 서비스에서 컨설팅 요약을 받아 온다. (`ai/worker/docs/job-contract.md` 5·6절)
 *
 * <p><b>실패를 사용자 응답으로 옮기지 않는다.</b> 요약은 전사가 남은 뒤 뒤에서 만들어지므로 기다리는
 * 요청이 없다. 갈래만 {@link SummaryFailedException} 에 담아 남기고, 요약은 비어 있는 채로 둔다.
 *
 * <p><b>읽기 한도를 다른 AI 호출과 따로 둔다.</b> 요약은 추론 모델을 써서 AI 가 GMS 를 120초까지
 * 기다린다. 확인 문장의 한도(25초)로는 정상 응답도 끊긴다. AI 의 상한보다 먼저 끊으면 AI 가 가려낸
 * 갈래가 닿지 못하므로 그보다 길게 둔다 ({@code app.ai.summary-read-timeout}, `job-contract.md` 8절).
 */
@Slf4j
@Component
public class ConsultingSummaryClient {
    private static final String PATH = "/consulting-summary";
    private static final String UNAVAILABLE = "AI_SERVICE_UNAVAILABLE";
    private static final String RESPONSE_INVALID = "AI_RESPONSE_INVALID";
    private static final String REQUEST_INVALID = "AI_REQUEST_INVALID";

    private final RestClient client;

    public ConsultingSummaryClient(
            RestClient.Builder builder,
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.connect-timeout}") Duration connectTimeout,
            @Value("${app.ai.summary-read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                // 기본값인 HTTP/2 는 http 주소에 업그레이드 요청(h2c)을 붙인다. AI 서버(uvicorn)는
                // 이를 지원하지 않아 큰 본문을 제대로 읽지 못하고 422 로 거절했다.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    /**
     * 전사 발화로 요약을 만든다.
     *
     * <p>기존 요구사항은 아직 넘기지 않는다. 넘기지 않으면 변경 후보가 모두 새로 넣는 것(`ADD`)으로
     * 나온다. 요청서 반영을 붙일 때 함께 넘긴다.
     *
     * @throws SummaryFailedException 닿지 못했거나, 계약과 다른 답이 왔거나, 이쪽이 잘못 보냈을 때
     */
    public AiSummary summarize(List<TranscriptResultRequest.Utterance> utterances) {
        AiSummary body;
        try {
            body = client.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("utterances", utterances, "requirements", List.of()))
                    .retrieve()
                    .body(AiSummary.class);
        } catch (RestClientResponseException exception) {
            throw mapFailure(exception);
        } catch (ResourceAccessException exception) {
            log.warn("consulting-summary 호출 실패: 닿지 못함 cause={}",
                    exception.getMostSpecificCause().toString());
            throw new SummaryFailedException(SummaryFailedException.Reason.UNAVAILABLE);
        } catch (RestClientException exception) {
            log.warn("consulting-summary 응답을 읽지 못함: cause={}", exception.getMostSpecificCause().toString());
            throw new SummaryFailedException(SummaryFailedException.Reason.RESPONSE_INVALID);
        }
        return read(body);
    }

    private SummaryFailedException mapFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String code;
        String message;
        try {
            Failure failure = exception.getResponseBodyAs(Failure.class);
            code = failure == null || failure.code() == null ? "" : failure.code();
            message = failure == null || failure.message() == null ? "" : failure.message();
        } catch (RuntimeException broken) {
            code = "";
            message = "본문이 계약 형식이 아니다";
        }
        SummaryFailedException.Reason reason = switch (code) {
            case UNAVAILABLE -> SummaryFailedException.Reason.UNAVAILABLE;
            case RESPONSE_INVALID -> SummaryFailedException.Reason.RESPONSE_INVALID;
            case REQUEST_INVALID -> SummaryFailedException.Reason.REQUEST_INVALID;
            default -> status == 503
                    ? SummaryFailedException.Reason.UNAVAILABLE
                    : status < 500
                            ? SummaryFailedException.Reason.REQUEST_INVALID
                            : SummaryFailedException.Reason.RESPONSE_INVALID;
        };
        if (reason == SummaryFailedException.Reason.REQUEST_INVALID) {
            // 이쪽이 계약과 다르게 보냈다. 다시 불러도 같으므로 결함으로 올린다.
            log.error("consulting-summary 호출 실패: 보낸 요청이 잘못됐다 status={} code={} reason={}",
                    status, code, message);
        } else {
            log.warn("consulting-summary 호출 실패: status={} code={} reason={}", status, code, message);
        }
        return new SummaryFailedException(reason);
    }

    /**
     * 상태가 {@code 200} 이어도 본문이 계약과 다를 수 있다. 네 배열이 모두 있어야 한다.
     *
     * <p>항목의 문장은 남기지 않는다. 상담 내용이 로그로 새지 않게 한다.
     */
    private AiSummary read(AiSummary body) {
        if (body == null
                || body.discussed() == null
                || body.decided() == null
                || body.nextActions() == null
                || body.changes() == null) {
            log.warn("consulting-summary 응답이 계약과 다름: 네 배열 중 빠진 것이 있다");
            throw new SummaryFailedException(SummaryFailedException.Reason.RESPONSE_INVALID);
        }
        return body;
    }

    /**
     * AI 서비스 응답. 항목마다 붙어 오는 근거 발화 순번과 검토 표시는 읽지 않는다 — AI 내부 값이다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiSummary(
            List<AiItem> discussed, List<AiItem> decided, List<AiItem> nextActions, List<AiChange> changes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiItem(String title, String detail) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiChange(SummaryContent.Kind kind, Integer issueId, String title, String detail) {}

    /** AI 서비스 실패 본문. (`job-contract.md` 6절) */
    record Failure(String code, String message) {}
}
