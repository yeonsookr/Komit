package com.a501.escrow.integration.finance;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.a501.escrow.config.FinanceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "app.finance", name = "enabled", havingValue = "true")
public class SsafyFinanceClient implements FinanceClient {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final FinanceProperties properties;
    private final Clock clock;
    private final ZoneId financeZone;

    public SsafyFinanceClient(RestClient.Builder builder, ObjectMapper objectMapper,
            FinanceProperties properties, Clock clock) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
        this.financeZone = ZoneId.of(properties.timeZone());
    }

    @Override
    public UserRegistration registerUser(String userId) {
        JsonNode response = post("/api/v1/member/", Map.of("apiKey", properties.apiKey(), "userId", userId));
        return new UserRegistration(text(response, "userId"), text(response, "userKey"));
    }

    @Override
    public Optional<UserRegistration> findUser(String userId) {
        try {
            JsonNode response = post("/api/v1/member/search",
                    Map.of("apiKey", properties.apiKey(), "userId", userId));
            return Optional.of(new UserRegistration(text(response, "userId"), text(response, "userKey")));
        } catch (FinanceApiException exception) {
            if ("E4003".equals(exception.code())) return Optional.empty();
            throw exception;
        }
    }

    @Override
    public DemandDepositAccount openDemandDepositAccount(String userKey, String accountTypeUniqueNo) {
        JsonNode record = record(postWithHeader("createDemandDepositAccount", userKey,
                Map.of("accountTypeUniqueNo", accountTypeUniqueNo)));
        return new DemandDepositAccount(text(record, "bankCode"), text(record, "accountNo"),
                "Komit 데모 입출금", "1", text(record.path("currency"), "currency"));
    }

    @Override
    public List<DemandDepositAccount> findDemandDepositAccounts(String userKey) {
        JsonNode records = postWithHeader("inquireDemandDepositAccountList", userKey, Map.of()).path("REC");
        List<DemandDepositAccount> result = new ArrayList<>();
        if (records.isArray()) {
            records.forEach(record -> result.add(new DemandDepositAccount(
                    text(record, "bankCode"), text(record, "accountNo"), text(record, "accountName"),
                    text(record, "accountTypeCode"), text(record, "currency"))));
        }
        return result;
    }

    @Override
    public String depositSeedMoney(String userKey, String accountNo, BigInteger amount, String requestReference) {
        JsonNode record = record(postWithHeader("updateDemandDepositAccountDeposit", userKey, requestReference, Map.of(
                "accountNo", accountNo,
                // 실호출 검증 결과 Q1001을 피하려면 금액은 JSON 문자열로 전송해야 합니다.
                "transactionBalance", amount.toString(),
                "transactionSummary", requestReference)));
        return text(record, "transactionUniqueNo");
    }

    @Override
    public List<DepositTransaction> findDeposits(
            String userKey, String accountNo, LocalDate startDate, LocalDate endDate) {
        return inquireTransactions(userKey, accountNo, startDate, endDate, "M", "ASC").stream()
                .map(item -> new DepositTransaction(item.transactionUniqueNo(),
                        new BigInteger(item.transactionBalance()), item.transactionSummary())).toList();
    }

    @Override
    public AccountAuthTransfer openAccountAuth(String userKey, String accountNo, String authText) {
        JsonNode record = record(postWithHeader("openAccountAuth", userKey,
                Map.of("accountNo", accountNo, "authText", authText)));
        return new AccountAuthTransfer(text(record, "transactionUniqueNo"));
    }

    @Override
    public List<AccountTransaction> inquireTransactions(String userKey, String accountNo,
            LocalDate startDate, LocalDate endDate, String transactionType, String orderByType) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("accountNo", accountNo);
        fields.put("startDate", startDate.format(DATE));
        fields.put("endDate", endDate.format(DATE));
        fields.put("transactionType", transactionType);
        if (orderByType != null && !orderByType.isBlank()) fields.put("orderByType", orderByType);
        JsonNode items = record(postWithHeader("inquireTransactionHistoryList", userKey, fields)).path("list");
        List<AccountTransaction> result = new ArrayList<>();
        if (items.isArray()) {
            items.forEach(item -> result.add(new AccountTransaction(
                    text(item, "transactionUniqueNo"), text(item, "transactionDate"),
                    text(item, "transactionTime"), text(item, "transactionType"),
                    text(item, "transactionTypeName"), text(item, "transactionBalance"),
                    text(item, "transactionAfterBalance"), optionalText(item, "transactionSummary"))));
        }
        return result;
    }

    private JsonNode postWithHeader(String apiName, String userKey, Map<String, Object> fields) {
        return postWithHeader(apiName, userKey, null, fields);
    }

    private JsonNode postWithHeader(
            String apiName, String userKey, String transactionReference, Map<String, Object> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Header", header(apiName, userKey, transactionReference));
        body.putAll(fields);
        return post("/api/v1/edu/" + switch (apiName) {
            case "openAccountAuth" -> "accountAuth/" + apiName;
            default -> "demandDeposit/" + apiName;
        }, body);
    }

    private Map<String, String> header(String apiName, String userKey, String transactionReference) {
        OffsetDateTime now = OffsetDateTime.now(clock).atZoneSameInstant(financeZone).toOffsetDateTime();
        Map<String, String> header = new LinkedHashMap<>();
        header.put("apiName", apiName);
        header.put("transmissionDate", now.format(DATE));
        header.put("transmissionTime", now.format(TIME));
        header.put("institutionCode", "00100");
        header.put("fintechAppNo", "001");
        header.put("apiServiceCode", apiName);
        header.put("institutionTransactionUniqueNo",
                transactionReference == null ? transactionNo(now) : transactionReference);
        header.put("apiKey", properties.apiKey());
        if (userKey != null) header.put("userKey", userKey);
        return header;
    }

    private String transactionNo(OffsetDateTime now) {
        int suffix = RANDOM.nextInt(1_000_000);
        return now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + String.format("%06d", suffix);
    }

    private JsonNode post(String path, Object body) {
        try {
            return restClient.post().uri(path).body(body).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String responseBody = new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        throw financeError(responseBody, response.getStatusCode().value());
                    }).body(JsonNode.class);
        } catch (FinanceApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new FinanceClientUnavailableException("SSAFY 금융망 호출에 실패했습니다.", exception);
        }
    }

    private FinanceApiException financeError(String responseBody, int status) {
        try {
            JsonNode error = objectMapper.readTree(responseBody);
            String code = optionalText(error, "responseCode");
            String message = optionalText(error, "responseMessage");
            if (code == null && error.has("Header")) {
                code = optionalText(error.path("Header"), "responseCode");
                message = optionalText(error.path("Header"), "responseMessage");
            }
            return new FinanceApiException(status, code == null ? "Q1000" : code,
                    message == null ? "SSAFY 금융망 오류" : message);
        } catch (JacksonException exception) {
            return new FinanceApiException(status, "Q1000", "SSAFY 금융망 오류 응답을 해석할 수 없습니다.");
        }
    }

    private static JsonNode record(JsonNode response) {
        JsonNode record = response.path("REC");
        if (record.isMissingNode() || record.isNull()) throw new FinanceApiException(502, "Q1000", "금융망 응답에 REC가 없습니다.");
        return record;
    }

    private static String text(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null || value.isBlank()) throw new FinanceApiException(502, "Q1000", "금융망 응답 필드가 없습니다: " + field);
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
