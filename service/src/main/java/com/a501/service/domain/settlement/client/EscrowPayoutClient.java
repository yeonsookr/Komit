package com.a501.service.domain.settlement.client;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.settlement.SettlementErrorCode;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Escrow 에 맡긴 대금을 작업자에게 보내 달라고 부탁하고, 그 결과를 묻는다.
 *
 * <p><b>지급 기록의 원본은 Escrow 다.</b> 이 서버는 지급 상태를 따로 저장하지 않고 매번
 * {@code inquireEscrow} 의 거래 목록을 읽는다. (`R-PAY-05`, `R-PAY-12`)
 *
 * <p><b>결과를 모르면 실패가 아니다.</b> 타임아웃·연결 실패·503·{@code Q1000} 은 돈이 나갔는지
 * 단정할 수 없으므로 {@link SettlementErrorCode#ESCROW_UNAVAILABLE} 로 옮긴다. 부르는 쪽은 같은
 * 멱등키로만 다시 부른다. (`escrow/dosc/BACKEND_ESCROW_INTEGRATION_GUIDE.md` 9절)
 *
 * <p><b>공용 {@code RestClient.Builder} 의 타임아웃을 그대로 쓰지 않는다.</b> 공용 설정은 읽기 5초인데
 * 지급은 Escrow 가 한 요청 안에서 금융망 예금주 조회·송금·수수료 이체를 차례로 끝내고 답한다. 먼저
 * 끊으면 끝난 지급도 결과 불명으로 남는다.
 */
@Slf4j
@Component
public class EscrowPayoutClient {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final String SUCCESS = "H0000";
    /** 금융망 처리 결과를 알 수 없을 때 Escrow 가 내는 코드. 실패로 확정하지 않는다. */
    private static final String UNKNOWN_RESULT = "Q1000";
    /** 신고로 동결된 에스크로. (`R-RPT-05`) */
    private static final String HELD = "E5007";
    private static final String HEADER = "Header";
    private static final String ESCROW_ID = "escrowId";
    private static final String STATUS = "status";
    private static final ObjectMapper ERROR_READER = new ObjectMapper();

    private final RestClient client;
    private final String clientApiKey;
    private final SecureRandom random = new SecureRandom();

    public EscrowPayoutClient(
            RestClient.Builder builder,
            @Value("${app.escrow.base-url}") String baseUrl,
            @Value("${app.escrow.client-api-key}") String clientApiKey,
            @Value("${app.escrow.connect-timeout}") Duration connectTimeout,
            @Value("${app.escrow.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        // 주입받은 빌더는 싱글턴이다. 사본을 떠서 이 클라이언트의 주소와 한도만 바꾼다. (BACKEND_CONVENTIONS 9장 Client)
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.clientApiKey = clientApiKey;
    }

    /**
     * 이 계약에 걸린 에스크로와 그 거래 목록. 예치를 건 적이 없으면 비어 있다.
     *
     * <p>이 서버는 {@code escrowId} 를 갖고 있지 않아 계약 id 로 목록을 먼저 묻는다.
     *
     * @param contractId Komit 계약 id 문자열. 예치할 때 건 값과 같다
     * @throws BusinessException {@link SettlementErrorCode#ESCROW_UNAVAILABLE} 답을 받지 못했을 때
     */
    public Optional<EscrowLedger> findByContract(String contractId) {
        Map<String, Object> listBody = new LinkedHashMap<>();
        listBody.put("contractIds", List.of(contractId));
        JsonNode found = post("inquireEscrowList", null, listBody);

        for (JsonNode item : found.path("list")) {
            if (contractId.equals(item.path("contractId").asString())) {
                long escrowId = item.path(ESCROW_ID).asLong();
                Map<String, Object> detailBody = new LinkedHashMap<>();
                detailBody.put(ESCROW_ID, escrowId);
                detailBody.put("contractId", contractId);
                return Optional.of(toLedger(escrowId, post("inquireEscrow", null, detailBody)));
            }
        }
        return Optional.empty();
    }

    /**
     * 대금 지급을 부탁한다. Escrow 가 수수료를 떼고 나머지를 보낸다. (`R-PAY-07`)
     *
     * <p>같은 멱등키로 다시 부르면 새로 보내지 않고 멈춘 지점부터 잇는다. 본문을 바꾸면 거절된다.
     *
     * @return 이번 정산의 상태. {@code COMPLETED} 가 아니면 아직 끝나지 않은 것이다
     * @throws BusinessException {@link SettlementErrorCode#ESCROW_UNAVAILABLE} 결과를 모를 때,
     *                           {@link SettlementErrorCode#ESCROW_HELD} 동결 중일 때,
     *                           {@link SettlementErrorCode#PAYOUT_REJECTED} 그 밖에 거절됐을 때
     */
    public String release(PayoutRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ESCROW_ID, request.escrowId());
        body.put("referenceId", request.referenceId());
        body.put("amount", request.amount());
        body.put("recipientBankCode", request.recipientBankCode());
        body.put("recipientAccountNo", request.recipientAccountNo());
        body.put("recipientName", request.recipientName());
        return post("releaseEscrow", request.idempotencyKey(), body).path(STATUS).asString();
    }

    /**
     * 남은 예치금 전액을 예치할 때 쓴 계좌로 돌려 달라고 부탁한다. (`R-PAY-04`)
     *
     * <p>Escrow 는 부분 환불을 받지 않고, 돌려받는 계좌가 예치 출금 계좌와 다르면 거절한다. 같은 멱등키로 다시
     * 부르면 새로 보내지 않고 멈춘 지점부터 잇는다.
     *
     * @return 이번 환불 거래의 상태. {@code COMPLETED} 가 아니면 아직 끝나지 않은 것이다
     * @throws BusinessException {@link #release(PayoutRequest)} 와 같은 갈래
     */
    public String refund(RefundRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ESCROW_ID, request.escrowId());
        body.put("amount", request.amount());
        body.put("refundBankCode", request.refundBankCode());
        body.put("refundAccountNo", request.refundAccountNo());
        body.put("refundHolderName", request.refundHolderName());
        body.put("referenceId", request.referenceId());
        return post("refundEscrow", request.idempotencyKey(), body).path(STATUS).asString();
    }

    private static EscrowLedger toLedger(long escrowId, JsonNode detail) {
        List<LedgerEntry> entries = new ArrayList<>();
        for (JsonNode item : detail.path("transactions")) {
            entries.add(new LedgerEntry(
                    item.path("transactionType").asString(),
                    item.path(STATUS).asString(),
                    item.path("referenceId").asString()));
        }
        return new EscrowLedger(
                escrowId,
                detail.path(STATUS).asString(),
                detail.path("isHeld").asBoolean(),
                detail.path("depositedAmount").bigIntegerValue(),
                detail.path("balance").bigIntegerValue(),
                List.copyOf(entries));
    }

    private JsonNode post(String name, String idempotencyKey, Map<String, Object> fields) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Map<String, String> header = new LinkedHashMap<>();
        header.put("apiName", name);
        header.put("transmissionDate", DATE.format(now));
        header.put("transmissionTime", TIME.format(now));
        header.put("institutionCode", "00100");
        header.put("fintechAppNo", "001");
        header.put("apiServiceCode", name);
        header.put("institutionTransactionUniqueNo", transactionNo(now));
        header.put("clientApiKey", clientApiKey);
        if (idempotencyKey != null) {
            header.put("idempotencyKey", idempotencyKey);
        }
        Map<String, Object> body = new LinkedHashMap<>(fields);
        body.put(HEADER, header);

        JsonNode response;
        try {
            response = client.post()
                    .uri("/api/v1/escrow/" + name)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw failure(name, exception.getStatusCode().value(), codeOf(exception));
        } catch (RestClientException exception) {
            log.warn("escrow unreachable api={} error={}", name, exception.getClass().getSimpleName());
            throw new BusinessException(SettlementErrorCode.ESCROW_UNAVAILABLE);
        }

        String code = response == null ? "" : response.path(HEADER).path("responseCode").asString();
        if (!SUCCESS.equals(code)) {
            throw failure(name, 200, code);
        }
        JsonNode rec = response.path("REC");
        if (rec.isMissingNode() || rec.isNull()) {
            log.warn("escrow response without REC api={}", name);
            throw new BusinessException(SettlementErrorCode.ESCROW_UNAVAILABLE);
        }
        return rec;
    }

    /** 응답 값은 남기지 않고 무엇이 왔는지(상태와 코드)만 남긴다. (BACKEND_CONVENTIONS 18.4) */
    private static BusinessException failure(String name, int status, String code) {
        if (status == 503 || UNKNOWN_RESULT.equals(code) || code.isEmpty()) {
            log.warn("escrow result unknown api={} status={} code={}", name, status, code);
            return new BusinessException(SettlementErrorCode.ESCROW_UNAVAILABLE);
        }
        if (HELD.equals(code)) {
            log.info("escrow held api={} code={}", name, code);
            return new BusinessException(SettlementErrorCode.ESCROW_HELD);
        }
        log.warn("escrow rejected api={} status={} code={}", name, status, code);
        return new BusinessException(SettlementErrorCode.PAYOUT_REJECTED);
    }

    /** 실패 본문의 {@code Header.responseCode}. 읽을 수 없으면 빈 문자열이다 — 결과 불명으로 다룬다. */
    private static String codeOf(RestClientResponseException exception) {
        try {
            return ERROR_READER.readTree(exception.getResponseBodyAsByteArray())
                    .path(HEADER).path("responseCode").asString();
        } catch (RuntimeException unreadable) {
            return "";
        }
    }

    /** 호출마다 새로 만드는 20자리 기관거래고유번호. 멱등키와 달리 재시도 때도 바꾼다. */
    private String transactionNo(LocalDateTime now) {
        return DATE.format(now) + TIME.format(now) + String.format("%06d", random.nextInt(1_000_000));
    }

    /**
     * 지급 요청 본문. 같은 지급을 다시 부를 때 모든 값이 처음과 같아야 한다.
     *
     * @param amount 수수료를 떼기 전 총액
     * @param recipientName 금융망에 등록된 예금주명 (`R-PAY-14`)
     */
    public record PayoutRequest(
            long escrowId,
            String referenceId,
            BigInteger amount,
            String recipientBankCode,
            String recipientAccountNo,
            String recipientName,
            String idempotencyKey) {
        @Override
        public String toString() {
            return "PayoutRequest[escrowId=" + escrowId + ", referenceId=" + referenceId + "]";
        }
    }

    /**
     * 환불 요청 본문. 같은 환불을 다시 부를 때 모든 값이 처음과 같아야 한다.
     *
     * @param amount 남은 예치금 전액
     * @param refundHolderName 금융망에 등록된 예금주명. Escrow 가 대조한다
     */
    public record RefundRequest(
            long escrowId,
            String referenceId,
            BigInteger amount,
            String refundBankCode,
            String refundAccountNo,
            String refundHolderName,
            String idempotencyKey) {
        @Override
        public String toString() {
            return "RefundRequest[escrowId=" + escrowId + ", referenceId=" + referenceId + "]";
        }
    }

    /**
     * Escrow 가 가진 에스크로 한 건. 상태·동결 여부·금액·거래 목록만 옮긴다.
     *
     * @param depositedAmount 예치가 확정된 금액. 지급 요청 금액과 같아야 한다
     * @param balance 아직 나가지 않은 금액. 환불은 이 금액 전액이다
     */
    public record EscrowLedger(
            long escrowId,
            String status,
            boolean held,
            BigInteger depositedAmount,
            BigInteger balance,
            List<LedgerEntry> transactions) {}

    /**
     * 에스크로 거래 한 건.
     *
     * @param type {@code DEPOSIT}·{@code RELEASE}·{@code FEE}·{@code REFUND}
     * @param status {@code PENDING}·{@code PROCESSING}·{@code UNKNOWN}·{@code COMPLETED}·{@code FAILED}
     * @param referenceId 정산 식별자. 예치는 비어 있을 수 있다
     */
    public record LedgerEntry(String type, String status, String referenceId) {}
}
