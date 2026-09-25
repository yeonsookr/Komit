package com.a501.service.domain.diagnosis.client;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.exception.ErrorCode;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionResponse;
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
 * AI 서비스에서 확인 문장 후보를 받아 온다.
 *
 * <p><b>실패는 상태 코드가 아니라 {@code code} 로 가른다.</b> AI 서비스는 실패를 셋으로 나눈다 —
 * 모델에 닿지 못한 것(`AI_SERVICE_UNAVAILABLE`), 닿았으나 계약에 맞는 결과를 못 만든 것
 * (`AI_RESPONSE_INVALID`), 그리고 **받은 요청이 계약과 다른 것**(`AI_REQUEST_INVALID`)이다.
 * 셋째는 AI 고장이 아니라 Service 가 잘못 보낸 것이므로 사용자에게 재시도를 권하면 안 된다.
 * (`ai/worker/docs/job-contract.md` 6절)
 *
 * <p><b>공용 {@code RestClient.Builder} 의 타임아웃을 그대로 쓰지 않는다.</b> 공용 설정은 읽기
 * 5초인데 AI 서비스는 모델 왕복을 더 오래 기다린다. 먼저 끊으면 AI 가 가려낸 갈래가 Service 에
 * 닿지 못하고 전부 연결 실패로 뭉개진다.
 */
@Slf4j
@Component
public class QaSuggestionClient {
    private static final String PATH = "/qa-suggestions";
    /** AI 계약의 실패 갈래. `job-contract.md` 6절이 정한 값이다. */
    private static final String UNAVAILABLE = "AI_SERVICE_UNAVAILABLE";

    private static final String RESPONSE_INVALID = "AI_RESPONSE_INVALID";
    private static final String REQUEST_INVALID = "AI_REQUEST_INVALID";
    /** `R-REQ-04`: 후보 3건, 각 문장 1~500자. 목 명세도 같은 값으로 고정돼 있다. */
    private static final int CANDIDATE_COUNT = 3;

    private static final int MAX_TEST_CASE_LENGTH = 500;

    private final RestClient client;

    public QaSuggestionClient(
            RestClient.Builder builder,
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.connect-timeout}") Duration connectTimeout,
            @Value("${app.ai.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                // 기본값인 HTTP/2 는 http 주소에 업그레이드 요청(h2c)을 붙이는데 AI 서버(uvicorn)가
                // 지원하지 않는다. 컨설팅 요약에서 큰 본문이 422 로 거절된 것을 보고 맞춘다.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        // 주입받은 빌더는 싱글턴이고 `requestFactory` 는 그 자리에서 값을 바꾼다. 그대로 쓰면 뒤에
        // 만들어지는 다른 클라이언트까지 이 한도를 물려받는다. 사본을 떠서 이 클라이언트 것만 바꾼다.
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    /**
     * 요구사항 문장 한 건으로 확인 문장 후보를 만든다.
     *
     * @throws BusinessException {@link DiagnosisErrorCode#AI_RESPONSE_INVALID} 계약과 다른 답이 왔을 때,
     *                           {@link DiagnosisErrorCode#AI_SERVICE_UNAVAILABLE} 닿지 못했을 때,
     *                           {@link CommonErrorCode#INTERNAL_ERROR} Service 가 계약과 다르게 보냈을 때
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
            throw new BusinessException(mapFailure(exception));
        } catch (ResourceAccessException exception) {
            // 연결 실패·타임아웃. 답 자체를 못 받았다.
            // `getMessage()` 는 겉껍데기라 무엇 때문인지 안 나온다. 가장 안쪽 원인을 남긴다.
            log.warn("qa-suggestion 호출 실패: 닿지 못함 cause={}",
                    exception.getMostSpecificCause().toString());
            throw new BusinessException(DiagnosisErrorCode.AI_SERVICE_UNAVAILABLE);
        } catch (RestClientException exception) {
            // 답은 왔는데 읽지 못했다. 본문이 JSON 이 아니거나 모양이 달랐다. 닿긴 했으므로 502 다.
            log.warn("qa-suggestion 응답을 읽지 못함: cause={}", rootCause(exception));
            throw new BusinessException(DiagnosisErrorCode.AI_RESPONSE_INVALID);
        }
        return read(body);
    }

    /**
     * 가장 안쪽 원인을 글로 만든다.
     *
     * <p>{@code RestClientException} 의 메시지는 대상 타입과 Content-Type 만 담고 진짜 원인은 원인
     * 예외에만 있다. 겉메시지만 찍으면 무엇이 잘못됐는지 로그로 알 수 없다.
     */
    private String rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.toString();
    }

    /**
     * 실패 본문의 {@code code} 로 갈래를 정한다.
     *
     * <p>본문을 못 읽으면 상태 코드로 보조 판정한다. {@code 4xx} 는 Service 가 잘못 보냈다는 뜻이므로
     * 사용자에게 재시도를 권하지 않는다.
     */
    private ErrorCode mapFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String code;
        String message;
        try {
            Failure failure = exception.getResponseBodyAs(Failure.class);
            code = failure == null || failure.code() == null ? "" : failure.code();
            message = failure == null || failure.message() == null ? "" : failure.message();
        } catch (RuntimeException broken) {
            // 계약 형식이 아닌 본문이다. 프록시가 끼어든 때가 이렇다. 아래에서 상태 코드로 가른다.
            // 여기서 바로 돌려주면 `4xx` 가 500 으로 갈리고도 ERROR 없이 나간다.
            code = "";
            message = "본문이 계약 형식이 아니다";
        }
        ErrorCode mapped = switch (code) {
            case UNAVAILABLE -> DiagnosisErrorCode.AI_SERVICE_UNAVAILABLE;
            case RESPONSE_INVALID -> DiagnosisErrorCode.AI_RESPONSE_INVALID;
            // Service 가 계약과 다르게 보냈다. AI 고장이 아니므로 재시도를 권하지 않는다.
            case REQUEST_INVALID -> CommonErrorCode.INTERNAL_ERROR;
            default -> fallback(status);
        };
        if (mapped == CommonErrorCode.INTERNAL_ERROR) {
            // Service 가 잘못 보낸 것이다. 거부 사유가 유일한 단서이므로 함께 남기고 ERROR 로 올린다.
            // `handleBusiness` 는 로그를 남기지 않아 여기서 안 남기면 500 이 조용히 나간다.
            log.error("qa-suggestion 호출 실패: 보낸 요청이 잘못됐다. 재시도해도 같다 status={} code={} reason={}",
                    status, code, message);
        } else {
            log.warn("qa-suggestion 호출 실패: status={} code={} reason={}", status, code, message);
        }
        return mapped;
    }

    private ErrorCode fallback(int status) {
        if (status == 503) {
            return DiagnosisErrorCode.AI_SERVICE_UNAVAILABLE;
        }
        return status < 500 ? CommonErrorCode.INTERNAL_ERROR : DiagnosisErrorCode.AI_RESPONSE_INVALID;
    }

    /**
     * 상태가 {@code 200} 이어도 본문이 계약과 다를 수 있다.
     *
     * <p>개수와 길이까지 본다. 화면과 목 명세가 「언제나 세 건」을 공표하므로, 두 건을 그대로
     * 내보내면 사용자는 원래 그런 줄 안다. (`R-REQ-04`)
     */
    private List<QaSuggestionResponse> read(Suggestions body) {
        if (body == null) {
            return rejected("본문이 비었다");
        }
        if (body.content() == null) {
            return rejected("content 칸이 없다");
        }
        if (body.content().size() != CANDIDATE_COUNT) {
            return rejected("후보가 %d 건이다. %d 건이어야 한다"
                    .formatted(body.content().size(), CANDIDATE_COUNT));
        }
        for (int i = 0; i < body.content().size(); i++) {
            QaSuggestionResponse row = body.content().get(i);
            if (row == null || row.testCase() == null) {
                return rejected("%d 번째 후보에 testCase 가 없다".formatted(i));
            }
            if (row.testCase().isBlank()) {
                return rejected("%d 번째 후보가 비어 있다".formatted(i));
            }
            if (row.testCase().length() > MAX_TEST_CASE_LENGTH) {
                return rejected("%d 번째 후보가 %d 자다. %d 자를 넘을 수 없다"
                        .formatted(i, row.testCase().length(), MAX_TEST_CASE_LENGTH));
            }
        }
        return body.content();
    }

    /**
     * 본문이 계약을 벗어났다.
     *
     * <p>이유를 관측값과 함께 남긴다. 「몇 건이 왔는지」와 「몇 자였는지」가 없으면 AI 쪽과 원인을
     * 나눌 수 없다. 문장 자체는 남기지 않는다 — 상담 내용이 로그로 새지 않게 한다.
     */
    private List<QaSuggestionResponse> rejected(String reason) {
        log.warn("qa-suggestion 응답이 계약과 다름: {}", reason);
        throw new BusinessException(DiagnosisErrorCode.AI_RESPONSE_INVALID);
    }

    /** AI 서비스 응답. 후보 배열 하나만 온다. */
    record Suggestions(List<QaSuggestionResponse> content) {}

    /** AI 서비스 실패 본문. 어떤 상태에서도 이 두 필드다. (`job-contract.md` 6절) */
    record Failure(String code, String message) {}
}
