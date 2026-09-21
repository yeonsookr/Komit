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
    private static final String API_KEY = "apiKey";
    private static final String USER_ID = "userId";
    private static final String USER_KEY = "userKey";
    private static final String BANK_CODE = "bankCode";
    private static final String ACCOUNT_NO = "accountNo";
    private static final String CURRENCY = "currency";
    private static final String TRANSACTION_NO = "transactionUniqueNo";
    private static final String TRANSACTION_BALANCE = "transactionBalance";
    private static final String VIRTUAL_ACCOUNT_ID = "virtualAccountId";
    private static final String HEADER = "Header";

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
        JsonNode response = post("/api/v1/member/", Map.of(API_KEY, properties.apiKey(), USER_ID, userId));
        return new UserRegistration(text(response, USER_ID), text(response, USER_KEY));
    }

    @Override
    public Optional<UserRegistration> findUser(String userId) {
        try {
            JsonNode response = post("/api/v1/member/search",
                    Map.of(API_KEY, properties.apiKey(), USER_ID, userId));
            return Optional.of(new UserRegistration(text(response, USER_ID), text(response, USER_KEY)));
        } catch (FinanceApiException exception) {
            if ("E4003".equals(exception.code())) return Optional.empty();
            throw exception;
        }
    }

    @Override
    public DemandDepositAccount openDemandDepositAccount(String userKey, String accountTypeUniqueNo) {
        JsonNode accountRecord = requiredRecord(postWithHeader("createDemandDepositAccount", userKey,
                Map.of("accountTypeUniqueNo", accountTypeUniqueNo)));
        // 상품명·계좌종류·통화는 개설 응답에 실릴 때만 채운다. 값을 지어내면 호출 측 확인이
        // 자기가 적은 값을 검사하게 되어 확인 구실을 못 한다.
        return new DemandDepositAccount(text(accountRecord, BANK_CODE), text(accountRecord, ACCOUNT_NO),
                optionalText(accountRecord, "accountName"), optionalText(accountRecord, "accountTypeCode"),
                optionalText(accountRecord.path(CURRENCY), CURRENCY));
    }

    @Override
    public List<DemandDepositAccount> findDemandDepositAccounts(String userKey) {
        JsonNode records = postWithHeader("inquireDemandDepositAccountList", userKey, Map.of()).path("REC");
        List<DemandDepositAccount> result = new ArrayList<>();
        if (records.isArray()) {
            records.forEach(account -> result.add(new DemandDepositAccount(
                    text(account, BANK_CODE), text(account, ACCOUNT_NO), text(account, "accountName"),
                    text(account, "accountTypeCode"), text(account, CURRENCY))));
        }
        return result;
    }

    @Override
    public AccountHolder inquireAccountHolder(String userKey, String accountNo) {
        JsonNode accountRecord = requiredRecord(postWithHeader("inquireDemandDepositAccount", userKey,
                Map.of(ACCOUNT_NO, accountNo)));
        return new AccountHolder(text(accountRecord, BANK_CODE), text(accountRecord, ACCOUNT_NO),
                text(accountRecord, "userName"));
    }

    @Override
    public BigInteger inquireAccountBalance(String userKey, String accountNo) {
        JsonNode accountBalanceRecord = requiredRecord(postWithHeader("inquireDemandDepositAccountBalance", userKey,
                Map.of(ACCOUNT_NO, accountNo)));
        return new BigInteger(text(accountBalanceRecord, "accountBalance"));
    }

    @Override
    public String depositSeedMoney(String userKey, String accountNo, BigInteger amount, String requestReference) {
        JsonNode depositRecord = requiredRecord(postWithHeader("updateDemandDepositAccountDeposit", userKey, requestReference, Map.of(
                ACCOUNT_NO, accountNo,
                // 실호출 검증 결과 Q1001을 피하려면 금액은 JSON 문자열로 전송해야 합니다.
                TRANSACTION_BALANCE, amount.toString(),
                "transactionSummary", requestReference)));
        return text(depositRecord, TRANSACTION_NO);
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
        JsonNode authRecord = requiredRecord(postWithHeader("openAccountAuth", userKey,
                Map.of(ACCOUNT_NO, accountNo, "authText", authText)));
        return new AccountAuthTransfer(text(authRecord, TRANSACTION_NO));
    }

    @Override
    public List<AccountTransaction> inquireTransactions(String userKey, String accountNo,
            LocalDate startDate, LocalDate endDate, String transactionType, String orderByType) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(ACCOUNT_NO, accountNo);
        fields.put("startDate", startDate.format(DATE));
        fields.put("endDate", endDate.format(DATE));
        fields.put("transactionType", transactionType);
        if (orderByType != null && !orderByType.isBlank()) fields.put("orderByType", orderByType);
        JsonNode items = requiredRecord(postWithHeader("inquireTransactionHistoryList", userKey, fields)).path("list");
        List<AccountTransaction> result = new ArrayList<>();
        if (items.isArray()) {
            items.forEach(item -> result.add(new AccountTransaction(
                    text(item, TRANSACTION_NO), text(item, "transactionDate"),
                    text(item, "transactionTime"), text(item, "transactionType"),
                    text(item, "transactionTypeName"), text(item, TRANSACTION_BALANCE),
                    text(item, "transactionAfterBalance"), optionalText(item, "transactionSummary"))));
        }
        return result;
    }

    @Override
    public VirtualAccount createVirtualAccount(String userKey, String mainAccountNo, String depositorName,
            BigInteger expectedAmount, LocalDate expiryDate, String requestReference) {
        JsonNode result = requiredRecord(postWithHeader("createVirtualAccount", userKey, requestReference, Map.of(
                "mainAccountNo", mainAccountNo,
                "depositorName", depositorName,
                "expectedAmount", expectedAmount.toString(),
                "expiryDate", expiryDate.format(DATE),
                "autoTransfer", false)));
        return new VirtualAccount(text(result, VIRTUAL_ACCOUNT_ID), text(result, "virtualAccountNo"));
    }

    @Override
    public String transfer(String userKey, String depositAccountNo, String withdrawalAccountNo,
            BigInteger amount, String depositSummary, String withdrawalSummary, String requestReference) {
        JsonNode result = requiredRecord(postWithHeader("updateDemandDepositAccountTransfer", userKey,
                requestReference, Map.of(
                        "depositAccountNo", depositAccountNo,
                        "withdrawalAccountNo", withdrawalAccountNo,
                        TRANSACTION_BALANCE, amount.toString(),
                        "depositTransactionSummary", depositSummary,
                        "withdrawalTransactionSummary", withdrawalSummary)));
        JsonNode withdrawal = transferWithdrawal(result, withdrawalAccountNo);
        return text(withdrawal, TRANSACTION_NO);
    }

    private static JsonNode transferWithdrawal(JsonNode result, String withdrawalAccountNo) {
        if (result.has("withdrawalTransaction")) return result.path("withdrawalTransaction");
        if (!result.isArray()) return result;
        for (JsonNode item : result) {
            if (withdrawalAccountNo.equals(optionalText(item, ACCOUNT_NO))) return item;
        }
        throw new FinanceApiException(502, "Q1000", "금융망 이체 응답에서 출금 거래를 찾을 수 없습니다.");
    }

    @Override
    public List<VirtualAccountDeposit> inquireVirtualAccountDeposits(String userKey, String virtualAccountId) {
        JsonNode result = requiredRecord(postWithHeader("inquireVirtualAccountDeposits", userKey,
                Map.of(VIRTUAL_ACCOUNT_ID, virtualAccountId)));
        JsonNode deposits = result.path("deposits");
        List<VirtualAccountDeposit> items = new ArrayList<>();
        if (deposits.isArray()) {
            deposits.forEach(item -> items.add(new VirtualAccountDeposit(
                    new BigInteger(text(item, "depositAmount")),
                    item.path("nameMatched").asBoolean(false),
                    item.path("transferred").asBoolean(false))));
        }
        return items;
    }

    @Override
    public VirtualAccountSettlement settleVirtualAccount(
            String userKey, String virtualAccountId, String requestReference) {
        JsonNode result = requiredRecord(postWithHeader("settleVirtualAccount", userKey, requestReference,
                Map.of(VIRTUAL_ACCOUNT_ID, virtualAccountId)));
        return new VirtualAccountSettlement(
                new BigInteger(text(result, "settledAmount")), text(result, "settledAt"));
    }

    @Override
    public String closeVirtualAccount(String userKey, String virtualAccountId, String requestReference) {
        JsonNode result = requiredRecord(postWithHeader("closeVirtualAccount", userKey, requestReference,
                Map.of(VIRTUAL_ACCOUNT_ID, virtualAccountId)));
        return text(result, "status");
    }

    private JsonNode postWithHeader(String apiName, String userKey, Map<String, Object> fields) {
        return postWithHeader(apiName, userKey, null, fields);
    }

    private JsonNode postWithHeader(
            String apiName, String userKey, String transactionReference, Map<String, Object> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(HEADER, header(apiName, userKey, transactionReference));
        body.putAll(fields);
        return post("/api/v1/edu/" + switch (apiName) {
            case "openAccountAuth" -> "accountAuth/" + apiName;
            case "createVirtualAccount", "inquireVirtualAccountDeposits",
                    "settleVirtualAccount", "closeVirtualAccount" -> "account/" + apiName;
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
        header.put(API_KEY, properties.apiKey());
        if (userKey != null) header.put(USER_KEY, userKey);
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
            if (code == null && error.has(HEADER)) {
                code = optionalText(error.path(HEADER), "responseCode");
                message = optionalText(error.path(HEADER), "responseMessage");
            }
            return new FinanceApiException(status, code == null ? "Q1000" : code,
                    message == null ? "SSAFY 금융망 오류" : message);
        } catch (JacksonException exception) {
            return new FinanceApiException(status, "Q1000", "SSAFY 금융망 오류 응답을 해석할 수 없습니다.");
        }
    }

    private static JsonNode requiredRecord(JsonNode response) {
        JsonNode responseRecord = response.path("REC");
        if (responseRecord.isMissingNode() || responseRecord.isNull()) throw new FinanceApiException(502, "Q1000", "금융망 응답에 REC가 없습니다.");
        return responseRecord;
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
