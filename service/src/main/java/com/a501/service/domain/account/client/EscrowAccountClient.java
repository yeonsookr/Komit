package com.a501.service.domain.account.client;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.account.AccountErrorCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class EscrowAccountClient {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");
    private final RestClient client;
    private final String clientApiKey;
    private final SecureRandom random = new SecureRandom();

    public EscrowAccountClient(
            RestClient.Builder builder,
            @Value("${app.escrow.base-url}") String baseUrl,
            @Value("${app.escrow.client-api-key}") String clientApiKey) {
        this.client = builder.baseUrl(baseUrl).build();
        this.clientApiKey = clientApiKey;
    }

    public MemberAccount createMemberAccount(String userId, String idempotencyKey) {
        JsonNode record = post("/api/v1/escrow/createMemberAccount", "createMemberAccount", null,
                idempotencyKey, Map.of("userId", userId));
        return new MemberAccount(required(record, "userKey"), required(record, "bankCode"),
                required(record, "accountNo"));
    }

    public OpenedAuth openAccountAuth(String userKey, String accountNo, String idempotencyKey) {
        JsonNode record = post("/api/v1/edu/accountAuth/openAccountAuth", "openAccountAuth", userKey,
                idempotencyKey, Map.of("accountNo", accountNo));
        return new OpenedAuth(required(record, "transactionUniqueNo"), required(record, "expiresAt"));
    }

    public void checkAuthCode(String userKey, String transactionUniqueNo, String accountNo, String authCode) {
        JsonNode record = post("/api/v1/edu/accountAuth/checkAuthCode", "checkAuthCode", userKey,
                null, Map.of("transactionUniqueNo", transactionUniqueNo, "accountNo", accountNo,
                        "authCode", authCode));
        if (!"SUCCESS".equals(required(record, "status"))) {
            throw new BusinessException(AccountErrorCode.VERIFICATION_NOT_FOUND);
        }
    }

    private JsonNode post(String path, String name, String userKey, String idempotencyKey,
            Map<String, String> fields) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Map<String, String> header = new LinkedHashMap<>();
        header.put("apiName", name);
        header.put("transmissionDate", DATE.format(now));
        header.put("transmissionTime", TIME.format(now));
        header.put("institutionCode", "00100");
        header.put("fintechAppNo", "001");
        header.put("apiServiceCode", name);
        header.put("institutionTransactionUniqueNo", transactionNo());
        header.put("clientApiKey", clientApiKey);
        if (idempotencyKey != null) header.put("idempotencyKey", idempotencyKey);
        if (userKey != null) header.put("userKey", userKey);
        Map<String, Object> body = new LinkedHashMap<>(fields);
        body.put("Header", header);

        try {
            JsonNode response = client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
            if (response == null || !"H0000".equals(response.path("Header").path("responseCode").asText())) {
                throw new BusinessException(AccountErrorCode.ESCROW_UNAVAILABLE);
            }
            JsonNode record = response.path("REC");
            if (record.isMissingNode() || record.isNull()) {
                throw new BusinessException(AccountErrorCode.ESCROW_UNAVAILABLE);
            }
            return record;
        } catch (RestClientResponseException exception) {
            throw mapFailure(exception, name);
        } catch (RestClientException exception) {
            throw new BusinessException(AccountErrorCode.ESCROW_UNAVAILABLE);
        }
    }

    private BusinessException mapFailure(RestClientResponseException exception, String operation) {
        String code = "";
        try {
            JsonNode error = new ObjectMapper()
                    .readTree(exception.getResponseBodyAsByteArray());
            code = error.path("Header").path("responseCode").asText();
        } catch (Exception ignored) {
            // 응답 본문이 깨진 경우 금융 처리 여부를 단정하지 않는다.
        }
        if ("openAccountAuth".equals(operation) && ("A1003".equals(code) || "H1009".equals(code))) {
            return new BusinessException(AccountErrorCode.ACCOUNT_REJECTED);
        }
        if ("checkAuthCode".equals(operation) && ("E5011".equals(code) || "E5012".equals(code))) {
            return new BusinessException(AccountErrorCode.VERIFICATION_NOT_FOUND);
        }
        return new BusinessException(AccountErrorCode.ESCROW_UNAVAILABLE);
    }

    private String transactionNo() {
        StringBuilder number = new StringBuilder(20);
        for (int i = 0; i < 20; i++) number.append(random.nextInt(10));
        return number.toString();
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw new BusinessException(AccountErrorCode.ESCROW_UNAVAILABLE);
        return value;
    }

    public record MemberAccount(String userKey, String bankCode, String accountNo) {}
    public record OpenedAuth(String transactionUniqueNo, String expiresAt) {}
}
