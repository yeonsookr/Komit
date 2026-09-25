package com.a501.service.domain.contract.client;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.exception.ErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.ContractDraftInput;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/** AI Worker에서 컨설팅 계약서 version 1의 내용을 받아 온다. */
@Slf4j
@Component
public class ContractDraftClient {
    private static final String PATH = "/contract-draft";
    private static final String CONSULTING = "CONSULTING";
    private static final String UNAVAILABLE = "AI_SERVICE_UNAVAILABLE";
    private static final String RESPONSE_INVALID = "AI_RESPONSE_INVALID";
    private static final String REQUEST_INVALID = "AI_REQUEST_INVALID";
    private static final int MAX_TOPIC_LENGTH = 255;

    private final RestClient client;

    public ContractDraftClient(
            RestClient.Builder builder,
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.connect-timeout}") Duration connectTimeout,
            @Value("${app.ai.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public GeneratedConsultingDraft generateConsulting(ContractDraftInput input) {
        if (!CONSULTING.equals(input.targetType())) {
            log.error("contract-draft 호출 실패: 컨설팅 호출에 다른 계약 유형을 보냄 type={}", input.targetType());
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        JsonNode body;
        try {
            body = client.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(input)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new BusinessException(mapFailure(exception));
        } catch (ResourceAccessException exception) {
            log.warn("contract-draft 호출 실패: 닿지 못함 cause={}",
                    exception.getMostSpecificCause().toString());
            throw new BusinessException(ContractErrorCode.AI_SERVICE_UNAVAILABLE);
        } catch (RestClientException exception) {
            log.warn("contract-draft 응답을 읽지 못함: cause={}", rootCause(exception));
            throw new BusinessException(ContractErrorCode.AI_RESPONSE_INVALID);
        }
        ConsultingDraft draft = read(body);
        return new GeneratedConsultingDraft(draft.topic(), draft.detail(), body.toString());
    }

    private ConsultingDraft read(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 1 || !body.has("draft")) {
            return rejected("응답은 draft 하나만 가져야 한다");
        }
        JsonNode draft = body.get("draft");
        if (draft == null || !draft.isObject() || draft.size() != 2
                || !draft.has("topic") || !draft.has("detail")) {
            return rejected("draft는 topic과 detail을 가져야 한다");
        }
        JsonNode topicNode = draft.get("topic");
        if (topicNode == null || !topicNode.isTextual() || topicNode.asText().isBlank()
                || topicNode.asText().length() > MAX_TOPIC_LENGTH) {
            return rejected("topic은 1자 이상 255자 이하여야 한다");
        }
        JsonNode detailNode = draft.get("detail");
        if (detailNode == null || (!detailNode.isNull()
                && (!detailNode.isTextual() || detailNode.asText().isBlank()))) {
            return rejected("detail은 null이거나 내용이 있는 문자열이어야 한다");
        }
        return new ConsultingDraft(
                topicNode.asText(), detailNode.isNull() ? null : detailNode.asText());
    }

    private ErrorCode mapFailure(RestClientResponseException exception) {
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
        ErrorCode mapped = switch (code) {
            case UNAVAILABLE -> ContractErrorCode.AI_SERVICE_UNAVAILABLE;
            case RESPONSE_INVALID -> ContractErrorCode.AI_RESPONSE_INVALID;
            case REQUEST_INVALID -> CommonErrorCode.INTERNAL_ERROR;
            default -> fallback(status);
        };
        if (mapped == CommonErrorCode.INTERNAL_ERROR) {
            log.error("contract-draft 호출 실패: 보낸 요청이 잘못됐다 status={} code={} reason={}",
                    status, code, message);
        } else {
            log.warn("contract-draft 호출 실패: status={} code={} reason={}", status, code, message);
        }
        return mapped;
    }

    private ErrorCode fallback(int status) {
        if (status == 503) {
            return ContractErrorCode.AI_SERVICE_UNAVAILABLE;
        }
        return status < 500 ? CommonErrorCode.INTERNAL_ERROR : ContractErrorCode.AI_RESPONSE_INVALID;
    }

    private String rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.toString();
    }

    private ConsultingDraft rejected(String reason) {
        log.warn("contract-draft 응답이 계약과 다름: {}", reason);
        throw new BusinessException(ContractErrorCode.AI_RESPONSE_INVALID);
    }

    public record ConsultingDraft(String topic, String detail) {}

    /** 검증된 컨설팅 필드와 생성 당시 AI 응답 전체. */
    public record GeneratedConsultingDraft(String topic, String detail, String contentSnapshot) {}

    record Failure(String code, String message) {}
}
