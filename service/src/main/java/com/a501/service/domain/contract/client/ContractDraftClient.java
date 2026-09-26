package com.a501.service.domain.contract.client;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.exception.ErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.ContractDraftInput;
import com.a501.service.domain.diagnosis.dto.DraftRequirements;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * AI Worker에서 계약서 version 1의 내용을 받아 온다.
 *
 * <p>개발 계약은 요청서와 채팅을 종합한 결과다. 요청서의 요구사항은 서버가 그대로 넣으므로 AI 는 채팅에서
 * 합의한 것만 돌려준다 — 요청서 요구사항에 더한 확인 문장, 요청서 밖의 새 작업.
 * (`docs/ai/chat-contract-draft.md`)
 */
@Slf4j
@Component
public class ContractDraftClient {
    private static final String PATH = "/contract-draft";
    private static final String CONSULTING = "CONSULTING";
    private static final String DEVELOPMENT = "DEVELOPMENT";
    private static final String UNAVAILABLE = "AI_SERVICE_UNAVAILABLE";
    private static final String RESPONSE_INVALID = "AI_RESPONSE_INVALID";
    private static final String REQUEST_INVALID = "AI_REQUEST_INVALID";
    private static final int MAX_TOPIC_LENGTH = 255;
    /** 새 작업의 이름·설명은 개발 계약서 작성 요청과 같은 한도다. */
    private static final int MAX_MILESTONE_TITLE_LENGTH = 200;

    private static final int MAX_MILESTONE_DETAIL_LENGTH = 1000;
    /** R-REQ-04: 확인 문장은 한 문장에 1~500자. */
    private static final int MAX_TEST_CASE_LENGTH = 500;

    /** 원본 스냅샷을 만들 때만 쓴다. 요청·응답 변환은 RestClient 가 맡는다. */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient client;

    public ContractDraftClient(
            RestClient.Builder builder,
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.connect-timeout}") Duration connectTimeout,
            @Value("${app.ai.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                // 기본값인 HTTP/2 는 h2c 업그레이드를 붙이는데 AI 서버(uvicorn)가 받지 않는다.
                .version(HttpClient.Version.HTTP_1_1)
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
        JsonNode body = call(input);
        ConsultingDraft draft = read(body);
        return new GeneratedConsultingDraft(draft.topic(), draft.detail(), body.toString());
    }

    /**
     * 개발 계약 초안을 받는다. 요청서 요구사항에 더한 확인 문장이 요청서에 없는 번호를 가리키면 계약을 어긴 결과다.
     *
     * <p>원본 스냅샷에는 AI 응답과 함께, 입력으로 보낸 고르지 않은 요구사항을 남긴다. 최초 AI 판단에 무엇이 쓰였는지
     * 보존하기 위해서다. 계약 데이터와 화면에는 없다.
     */
    public GeneratedDevelopmentDraft generateDevelopment(ContractDraftInput input) {
        if (!DEVELOPMENT.equals(input.targetType())) {
            log.error("contract-draft 호출 실패: 개발 호출에 다른 계약 유형을 보냄 type={}", input.targetType());
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        JsonNode body = call(input);
        Set<Integer> requested = input.issues().stream()
                .map(DraftRequirements.Requested::issueId)
                .collect(Collectors.toSet());
        GeneratedDevelopmentDraft draft = readDevelopment(body, requested);
        ObjectNode snapshot = ((ObjectNode) body).deepCopy();
        snapshot.set("rejectedIssues", JSON.valueToTree(input.rejectedIssues()));
        return new GeneratedDevelopmentDraft(draft.criteriaAdditions(), draft.newMilestones(), snapshot.toString());
    }

    private JsonNode call(ContractDraftInput input) {
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
        return body;
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

    private GeneratedDevelopmentDraft readDevelopment(JsonNode body, Set<Integer> requested) {
        if (body == null || !body.isObject() || body.size() != 1 || !body.has("draft")) {
            rejected("응답은 draft 하나만 가져야 한다");
        }
        JsonNode draft = body.get("draft");
        if (draft == null || !draft.isObject() || draft.size() != 2
                || !draft.has("criteriaAdditions") || !draft.has("newMilestones")) {
            rejected("draft는 criteriaAdditions와 newMilestones를 가져야 한다");
        }
        List<CriteriaAddition> additions = new ArrayList<>();
        Set<Integer> added = new HashSet<>();
        for (JsonNode row : array(draft.get("criteriaAdditions"), "criteriaAdditions")) {
            if (!row.isObject() || row.size() != 2 || !row.has("issueId") || !row.has("qaCriteria")) {
                rejected("확인 문장 추가는 issueId와 qaCriteria만 가져야 한다");
            }
            JsonNode issueId = row.get("issueId");
            if (!issueId.isIntegralNumber() || !requested.contains(issueId.asInt()) || !added.add(issueId.asInt())) {
                rejected("확인 문장 추가는 요청서의 요구사항을 한 번씩만 가리켜야 한다");
            }
            List<String> criteria = criteria(row.get("qaCriteria"));
            if (criteria.isEmpty()) {
                rejected("추가할 확인 문장이 비었다");
            }
            additions.add(new CriteriaAddition(issueId.asInt(), criteria));
        }
        List<NewMilestone> milestones = new ArrayList<>();
        for (JsonNode row : array(draft.get("newMilestones"), "newMilestones")) {
            if (!row.isObject() || row.size() != 3
                    || !row.has("title") || !row.has("detail") || !row.has("qaCriteria")) {
                rejected("새 작업은 title·detail·qaCriteria만 가져야 한다");
            }
            JsonNode title = row.get("title");
            if (!title.isTextual() || title.asText().isBlank() || title.asText().length() > MAX_MILESTONE_TITLE_LENGTH) {
                rejected("새 작업 이름은 1자 이상 200자 이하여야 한다");
            }
            JsonNode detail = row.get("detail");
            if (!detail.isNull() && (!detail.isTextual() || detail.asText().isBlank()
                    || detail.asText().length() > MAX_MILESTONE_DETAIL_LENGTH)) {
                rejected("새 작업 설명은 null이거나 1자 이상 1000자 이하여야 한다");
            }
            milestones.add(new NewMilestone(
                    title.asText(), detail.isNull() ? null : detail.asText(), criteria(row.get("qaCriteria"))));
        }
        return new GeneratedDevelopmentDraft(additions, milestones, null);
    }

    private Iterable<JsonNode> array(JsonNode node, String field) {
        if (node == null || !node.isArray()) {
            rejected(field + "는 배열이어야 한다");
        }
        return node;
    }

    /** 확인 문장 목록. 합의한 문장이 없으면 비어 있다. 문장마다 1~500자다. (R-REQ-04) */
    private List<String> criteria(JsonNode node) {
        List<String> sentences = new ArrayList<>();
        for (JsonNode sentence : array(node, "qaCriteria")) {
            if (!sentence.isTextual() || sentence.asText().isBlank()
                    || sentence.asText().length() > MAX_TEST_CASE_LENGTH) {
                rejected("확인 문장은 1자 이상 500자 이하여야 한다");
            }
            sentences.add(sentence.asText());
        }
        return sentences;
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

    /**
     * 검증된 개발 계약 초안과 원본 스냅샷.
     *
     * @param criteriaAdditions 요청서 요구사항에 채팅에서 새로 합의한 확인 문장
     * @param newMilestones 채팅에서 요청서 밖에 더 하기로 합의한 작업
     * @param contentSnapshot AI 응답 전체와 입력으로 보낸 고르지 않은 요구사항
     */
    public record GeneratedDevelopmentDraft(
            List<CriteriaAddition> criteriaAdditions, List<NewMilestone> newMilestones, String contentSnapshot) {
        public GeneratedDevelopmentDraft {
            criteriaAdditions = List.copyOf(criteriaAdditions);
            newMilestones = List.copyOf(newMilestones);
        }

        /** 요구사항별로 더할 확인 문장. */
        public Map<Integer, List<String>> additionsByIssue() {
            return criteriaAdditions.stream()
                    .collect(Collectors.toMap(CriteriaAddition::issueId, CriteriaAddition::qaCriteria));
        }
    }

    /** 요청서 요구사항 하나에 더할 확인 문장. */
    public record CriteriaAddition(Integer issueId, List<String> qaCriteria) {
        public CriteriaAddition {
            qaCriteria = List.copyOf(qaCriteria);
        }
    }

    /** 채팅에서 합의한 새 작업 하나. 합의한 확인 문장이 없으면 {@code qaCriteria} 가 비어 있다. */
    public record NewMilestone(String title, String detail, List<String> qaCriteria) {
        public NewMilestone {
            qaCriteria = List.copyOf(qaCriteria);
        }
    }

    record Failure(String code, String message) {}
}
