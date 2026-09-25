package com.a501.service.domain.contract.client;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.contract.ContractErrorCode;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * 예치를 Escrow 서버에 맡긴다.
 *
 * <p><b>돈의 원장은 이 서버가 갖지 않는다.</b> 결제와 보관은 Escrow 가 맡고 여기서는 걸어 달라고
 * 부탁하고 상태를 물을 뿐이다. (`R-PAY-05`)
 *
 * <p>계좌 쪽 {@code EscrowAccountClient} 와 같은 규약을 쓴다. 헤더에 API 키와 거래 고유번호를 싣고
 * 응답의 {@code Header.responseCode} 가 성공이어야 {@code REC} 를 읽는다.
 */
@Component
public class EscrowDepositClient {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final String SUCCESS = "H0000";

    private final RestClient client;
    private final String clientApiKey;
    private final SecureRandom random = new SecureRandom();

    public EscrowDepositClient(
            RestClient.Builder builder,
            @Value("${app.escrow.base-url}") String baseUrl,
            @Value("${app.escrow.client-api-key}") String clientApiKey) {
        this.client = builder.baseUrl(baseUrl).build();
        this.clientApiKey = clientApiKey;
    }

    /**
     * 예치를 건다.
     *
     * <p>같은 열쇠로 두 번 불러도 한 번만 나간다. Escrow 가 멱등키로 가른다.
     *
     * <p>{@code userKey} 는 회원가입 때 Escrow 가 내준 의뢰인의 열쇠다. 그 열쇠로 발급한 계좌가
     * 아니면 Escrow 가 거절한다.
     */
    public Deposit create(
            String contractId,
            String contractType,
            String depositorName,
            BigInteger amount,
            String userKey,
            String withdrawalBankCode,
            String withdrawalAccountNo,
            String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contractId", contractId);
        body.put("contractType", contractType);
        body.put("depositorName", depositorName);
        body.put("amount", amount);
        body.put("withdrawalBankCode", withdrawalBankCode);
        body.put("withdrawalAccountNo", withdrawalAccountNo);

        JsonNode record =
                post("/api/v1/escrow/createEscrow", "createEscrow", userKey, idempotencyKey, body);
        return new Deposit(
                record.path("escrowId").asLong(),
                contractId,
                record.path("status").asText(),
                record.path("depositedAmount").bigIntegerValue(),
                record.path("balance").bigIntegerValue());
    }

    /**
     * 이 계약의 예치를 찾는다. 건 적이 없으면 비어 있다.
     *
     * <p><b>목록으로 묻는다.</b> 단건 조회는 {@code escrowId} 를 요구하는데 이 서버는 그 번호를
     * 갖고 있지 않다. 원장을 두지 않기로 한 대가다.
     */
    public Optional<Deposit> findByContract(String contractId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contractIds", List.of(contractId));

        JsonNode found =
                post("/api/v1/escrow/inquireEscrowList", "inquireEscrowList", null, null, body);
        for (JsonNode item : found.path("list")) {
            if (contractId.equals(item.path("contractId").asText())) {
                return Optional.of(inquire(item.path("escrowId").asLong(), contractId));
            }
        }
        return Optional.empty();
    }

    /**
     * 예치 한 건을 자세히 읽는다.
     *
     * <p>목록은 잔액만 주고 걸어 둔 금액은 주지 않는다. 금액까지 옮기려면 한 번 더 물어야 한다.
     */
    private Deposit inquire(long escrowId, String contractId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("escrowId", escrowId);
        body.put("contractId", contractId);

        JsonNode record = post("/api/v1/escrow/inquireEscrow", "inquireEscrow", null, null, body);
        return new Deposit(
                escrowId,
                contractId,
                record.path("status").asText(),
                record.path("depositedAmount").bigIntegerValue(),
                record.path("balance").bigIntegerValue());
    }

    private JsonNode post(
            String path,
            String name,
            String userKey,
            String idempotencyKey,
            Map<String, Object> fields) {
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
        if (userKey != null) {
            header.put("userKey", userKey);
        }
        if (idempotencyKey != null) {
            header.put("idempotencyKey", idempotencyKey);
        }

        Map<String, Object> body = new LinkedHashMap<>(fields);
        body.put("Header", header);

        try {
            JsonNode response = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null
                    || !SUCCESS.equals(response.path("Header").path("responseCode").asText())) {
                throw new BusinessException(ContractErrorCode.ESCROW_UNAVAILABLE);
            }
            JsonNode record = response.path("REC");
            if (record.isMissingNode() || record.isNull()) {
                throw new BusinessException(ContractErrorCode.ESCROW_UNAVAILABLE);
            }
            return record;
        } catch (RestClientException exception) {
            // 돈이 오갔는지 단정할 수 없다. 실패로 답하고 같은 멱등키로 다시 부르게 한다.
            throw new BusinessException(ContractErrorCode.ESCROW_UNAVAILABLE);
        }
    }

    private String transactionNo() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return DATE.format(now) + TIME.format(now) + String.format("%06d", random.nextInt(1_000_000));
    }

    /** Escrow 가 돌려준 예치 한 건. 금액과 상태만 읽고 원장은 그쪽에 둔다. */
    public record Deposit(
            long escrowId,
            String contractId,
            String status,
            BigInteger depositedAmount,
            BigInteger balance) {}
}
