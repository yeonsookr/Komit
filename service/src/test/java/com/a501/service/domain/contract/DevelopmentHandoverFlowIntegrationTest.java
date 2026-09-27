package com.a501.service.domain.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.domain.account.crypto.AccountDataCrypto;
import com.a501.service.domain.account.entity.Account;
import com.a501.service.domain.account.entity.MemberAccountProvision;
import com.a501.service.domain.account.repository.AccountRepository;
import com.a501.service.domain.account.repository.MemberAccountProvisionRepository;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.developer.entity.Developer;
import com.a501.service.domain.developer.repository.DeveloperRepository;
import com.a501.service.domain.diagnosis.entity.Diagnosis;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.repository.DiagnosisRepository;
import com.a501.service.domain.diagnosis.repository.InspectionRepository;
import com.a501.service.domain.project.entity.Project;
import com.a501.service.domain.project.repository.ProjectRepository;
import com.a501.service.domain.room.entity.Room;
import com.a501.service.domain.room.repository.RoomRepository;
import com.a501.service.domain.settlement.service.DevelopmentPayoutService;
import com.a501.service.domain.user.entity.User;
import com.a501.service.domain.user.repository.UserRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 개발 계약 하나를 작성부터 전액 지급까지 진짜 Tomcat·PostgreSQL·MinIO 로 끝까지 태운다.
 *
 * <p>단위 시험은 단계마다 따로 본다. 여기서는 단계가 이어질 때 상태가 제대로 넘어가는지 본다. 일반 작업을 모두
 * 통과해야 인계가 열리고, 최종 인수 시험을 통과해야 지급 대상이 되며, 지급이 끝나야 해금 파일과 리뷰가 열린다.
 * (`R-QA-02`, `R-QA-06`, `R-PAY-01`)
 *
 * <p>Escrow 만 가짜로 둔다. 돈이 오가는 부분은 이 서버가 아니라 Escrow 의 원장을 읽어 정하므로, 가짜도 원장을
 * 들고 있다가 지급을 받으면 지급·수수료 거래를 목록에 더한다.
 */
@Testcontainers
// MOCK 환경에는 ServerContainer가 없어 WebSocketConfig가 기동에 실패하므로 실제 서블릿 컨테이너를 띄운다.
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
// 지급·자동 통과 스케줄러가 시험 도중에 돌면 지급 잠금을 먼저 잡아 직접 부른 지급이 BUSY 로 끝난다.
@TestPropertySource(properties = {
        "app.settlement.payout-initial-delay=1h",
        "app.contract.auto-pass-initial-delay=1h"})
@DisplayName("개발 계약 인계와 전액 지급 흐름")
class DevelopmentHandoverFlowIntegrationTest {
    private static final String BUCKET = "komit-test";
    private static final String ESCROW_PREFIX = "/api/v1/escrow/";
    private static final int SSAFY_BANK_CODE_ID = 58;
    private static final String DEVELOPER_ACCOUNT_NO = "0011234567890123";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    @Container
    static MinIOContainer minio = new MinIOContainer(
            DockerImageName.parse("komit2026/minio:RELEASE.2025-09-07T16-13-09Z")
                    .asCompatibleSubstituteFor("minio/minio"));

    /** 계약 id 별 예치 금액. */
    private static final Map<String, Long> DEPOSITS = new ConcurrentHashMap<>();
    private static final Map<Long, String> ESCROW_CONTRACTS = new ConcurrentHashMap<>();
    private static final Set<String> RELEASED = ConcurrentHashMap.newKeySet();
    private static final List<JsonNode> RELEASE_CALLS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger MEMBER_ACCOUNTS = new AtomicInteger();
    private static final AtomicLong ESCROW_IDS = new AtomicLong(9000);
    // 컨텍스트가 주소를 읽기 전에 떠 있어야 한다.
    private static final HttpServer ESCROW = startEscrow();

    @LocalServerPort
    int port;

    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired DevelopmentPayoutService developmentPayoutService;
    @Autowired AccountDataCrypto crypto;
    @Autowired UserRepository userRepository;
    @Autowired DeveloperRepository developerRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired DiagnosisRepository diagnosisRepository;
    @Autowired InspectionRepository inspectionRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired MemberAccountProvisionRepository memberAccountProvisionRepository;
    @Autowired DevelopmentContractRepository developmentContractRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.internal-endpoint", minio::getS3URL);
        registry.add("app.storage.public-endpoint", minio::getS3URL);
        registry.add("app.storage.access-key", minio::getUserName);
        registry.add("app.storage.secret-key", minio::getPassword);
        registry.add("app.storage.bucket", () -> BUCKET);
        registry.add("app.escrow.base-url", () -> "http://127.0.0.1:" + ESCROW.getAddress().getPort());
    }

    @BeforeAll
    static void createBucket() throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint(minio.getS3URL())
                .credentials(minio.getUserName(), minio.getPassword())
                .build();
        client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
    }

    @AfterAll
    static void stopEscrow() {
        ESCROW.stop(0);
    }

    @Test
    @DisplayName("일반 작업을 모두 통과하고 인계를 확인하면 전액이 한 번 나가고, 그 뒤에야 해금 파일과 리뷰가 열린다")
    void developmentContractFromWritingToFullPayout() throws Exception {
        User client = userRepository.save(user(1001L, "의뢰인", "client@komit.test"));
        User dev = userRepository.save(user(1002L, "개발자", "dev@komit.test"));
        User stranger = userRepository.save(user(1003L, "제삼자", "stranger@komit.test"));
        Developer developer = developerRepository.save(Developer.register(dev.getId()));
        Project project = projectRepository.save(Project.of(client.getId(), "수제 비누 가게", null));
        // 새로 합의한 작업은 요구사항이 되어 최근 검사에 붙으므로 진단과 검사가 있어야 한다.
        Diagnosis diagnosis = diagnosisRepository.save(Diagnosis.of(project.getId()));
        inspectionRepository.save(Inspection.of(diagnosis.getId(), (short) 1, null, null));
        Room inquiry = roomRepository.saveAndFlush(Room.openInquiry(project.getId(), developer.getId(), null));
        inquiry.assignChatRef();
        roomRepository.saveAndFlush(inquiry);
        // R-PAY-14: 지급 계좌는 싸피은행 등록 계좌와 금융망 회원 계정이 함께 있어야 찾는다.
        accountRepository.save(Account.register(dev.getId(), "개발자", "001-****-0123", SSAFY_BANK_CODE_ID,
                crypto.encrypt(DEVELOPER_ACCOUNT_NO)));
        memberAccountProvisionRepository.save(MemberAccountProvision.pending(dev.getId(), "prov-dev"));
        Integer clientId = client.getId();
        Integer devId = dev.getId();

        JsonNode converted = expect(send("POST", "/api/rooms/" + inquiry.getId() + "/conversion", clientId,
                Map.of("targetType", "DEVELOPMENT")), 201);
        int roomId = converted.path("convertedRoomId").asInt();
        assertThat(converted.path("convertedRoomType").asString()).isEqualTo("DEVELOPMENT");

        // 계약서 작성과 서명
        JsonNode written = expect(send("POST", "/api/rooms/" + roomId + "/development-contracts", devId,
                contractBody()), 201);
        int contractId = written.path("contractId").asInt();
        JsonNode development = written.path("development");
        assertThat(development.path("totalAmount").asLong()).isEqualTo(650_000L);
        JsonNode first = milestone(development, "1차");
        JsonNode second = milestone(development, "2차");
        JsonNode handover = milestone(development, "인계");
        assertThat(handover.path("isHandover").asBoolean()).isTrue();
        assertThat(first.path("isHandover").asBoolean()).isFalse();
        int deliverableId = development.path("deliverables").get(0).path("deliverableId").asInt();

        expect(send("POST", "/api/contracts/" + contractId + "/signatures", clientId,
                Map.of("signatureImageFileId", 1)), 201);
        expect(send("POST", "/api/contracts/" + contractId + "/signatures", devId,
                Map.of("signatureImageFileId", 1)), 201);

        // 예치와 착수
        expect(send("POST", "/api/contracts/" + contractId + "/deposit", clientId,
                Map.of("paymentMethodId", 1, "idempotencyKey", "dep-" + contractId)), 201);
        assertThat(DEPOSITS.get(String.valueOf(contractId))).isEqualTo(650_000L);

        JsonNode started = expect(send("POST", "/api/contracts/" + contractId + "/start", devId, null), 200);
        assertThat(started.path("development").path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(milestone(started.path("development"), "1차").path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(milestone(started.path("development"), "2차").path("status").asString()).isEqualTo("SCHEDULED");
        assertThat(milestone(started.path("development"), "인계").path("status").asString()).isEqualTo("SCHEDULED");

        // R-QA-06: 일반 작업이 남아 있으면 인계는 열리지 않는다.
        expectError(send("POST", "/api/milestones/" + id(handover) + "/submission", devId, null),
                409, "HANDOVER_NOT_OPEN");
        expectError(send("POST", "/api/deliverables/" + deliverableId + "/urls", devId,
                Map.of("url", "https://demo.komit.example.com")), 409, "HANDOVER_NOT_OPEN");

        passRegularMilestone(first, criterionIds(second).get(0), clientId, devId);
        passRegularMilestone(second, criterionIds(first).get(0), clientId, devId);

        // 인계 자료 등록
        byte[] testBytes = "시험본 내용".getBytes(StandardCharsets.UTF_8);
        byte[] paidBytes = "소스 전체 내용".getBytes(StandardCharsets.UTF_8);
        long testFileId = upload(devId, "시험본.zip", testBytes);
        long paidFileId = upload(devId, "소스 전체.zip", paidBytes);

        expect(send("POST", "/api/deliverables/" + deliverableId + "/file", devId,
                Map.of("fileId", testFileId, "accessStage", "TEST")), 201);
        expect(send("POST", "/api/deliverables/" + deliverableId + "/file", devId,
                Map.of("fileId", paidFileId, "accessStage", "PAID")), 201);
        JsonNode registered = expect(send("POST", "/api/deliverables/" + deliverableId + "/urls", devId,
                Map.of("url", "https://demo.komit.example.com")), 201);
        expectError(send("POST", "/api/deliverables/" + deliverableId + "/file", devId,
                Map.of("fileId", testFileId, "accessStage", "PAID")), 409, "MATERIAL_STAGE_CONFLICT");

        JsonNode materials = registered.path("materials");
        assertThat(materials).hasSize(3);
        int testMaterialId = materialId(materials, "fileId", String.valueOf(testFileId));
        int paidMaterialId = materialId(materials, "fileId", String.valueOf(paidFileId));
        int urlMaterialId = materialId(materials, "url", "https://demo.komit.example.com");

        // 최종 인수 시험
        JsonNode handoverSubmitted = expect(
                send("POST", "/api/milestones/" + id(handover) + "/submission", devId, null), 201);
        assertThat(handoverSubmitted.path("status").asString()).isEqualTo("SUBMITTED");
        expectError(send("POST", "/api/deliverables/" + deliverableId + "/urls", devId,
                Map.of("url", "https://late.komit.example.com")), 409, "HANDOVER_UNDER_REVIEW");

        JsonNode handoverConfirmed = expect(send("POST", "/api/milestones/" + id(handover) + "/confirmation",
                clientId, Map.of("qaCriterionIds", criterionIds(handover))), 201);
        assertThat(handoverConfirmed.path("status").asString()).isEqualTo("CONFIRMED");
        expectError(send("POST", "/api/deliverables/" + deliverableId + "/urls", devId,
                Map.of("url", "https://late.komit.example.com")), 409, "FINAL_ACCEPTANCE_PASSED");

        // 지급 전: 시험용만 의뢰인에게 열린다.
        HttpResponse<byte[]> testDownload = download(testMaterialId, clientId);
        assertThat(testDownload.statusCode()).isEqualTo(200);
        assertThat(testDownload.headers().firstValue("Content-Type")).hasValue("application/octet-stream");
        assertThat(testDownload.headers().firstValue("Content-Disposition"))
                .hasValueSatisfying(value -> assertThat(value).contains("filename*=UTF-8''"));
        assertThat(testDownload.body()).containsExactly(testBytes);

        HttpResponse<byte[]> lockedDownload = download(paidMaterialId, clientId);
        assertThat(lockedDownload.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(lockedDownload.body()).path("code").asString())
                .isEqualTo("DELIVERABLE_ACCESS_LOCKED");

        HttpResponse<byte[]> developerDownload = download(paidMaterialId, devId);
        assertThat(developerDownload.statusCode()).isEqualTo(200);
        assertThat(developerDownload.body()).containsExactly(paidBytes);

        assertThat(download(paidMaterialId, stranger.getId()).statusCode()).isEqualTo(404);
        assertThat(download(urlMaterialId, clientId).statusCode()).isEqualTo(404);

        expectError(send("POST", "/api/contracts/" + contractId + "/review", clientId,
                Map.of("devScore", 5, "expScore", 5)), 400, "CONTRACT_NOT_COMPLETED");
        assertThat(expect(send("GET", "/api/contracts/" + contractId, clientId, null), 200)
                .path("development").path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(RELEASE_CALLS).isEmpty();

        // 지급
        developmentPayoutService.payDue();

        assertThat(RELEASE_CALLS).hasSize(1);
        JsonNode release = RELEASE_CALLS.get(0);
        assertThat(release.path("amount").asLong()).isEqualTo(650_000L);
        assertThat(release.path("referenceId").asString()).isEqualTo("SETTLEMENT:DEVELOPMENT-" + contractId);
        assertThat(release.path("Header").path("idempotencyKey").asString())
                .isEqualTo("escrow:release:development-contract-" + contractId);
        assertThat(release.path("recipientBankCode").asString()).isEqualTo("001");
        assertThat(release.path("recipientAccountNo").asString()).isEqualTo(DEVELOPER_ACCOUNT_NO);
        assertThat(release.path("recipientName").asString()).isEqualTo("komit-" + devId);
        assertThat(developmentContractRepository.findByContractIdAndDeletedFalse(contractId))
                .map(DevelopmentContract::getStatus)
                .hasValue("COMPLETED");

        // 완료된 계약은 다시 지급 대상이 되지 않는다.
        developmentPayoutService.payDue();
        assertThat(RELEASE_CALLS).hasSize(1);

        // 지급 뒤
        HttpResponse<byte[]> unlockedDownload = download(paidMaterialId, clientId);
        assertThat(unlockedDownload.statusCode()).isEqualTo(200);
        assertThat(unlockedDownload.body()).containsExactly(paidBytes);
        expect(send("POST", "/api/contracts/" + contractId + "/review", clientId,
                Map.of("devScore", 5, "expScore", 5)), 201);
        assertThat(expect(send("GET", "/api/contracts/" + contractId, clientId, null), 200)
                .path("development").path("status").asString()).isEqualTo("COMPLETED");
    }

    /**
     * 일반 작업 하나를 통과시킨다. 일부만 체크하면 작업으로 돌아가고, 다시 확인을 요청한 뒤 모두 체크해야 통과한다.
     *
     * @param foreignCriterionId 다른 작업의 확인 문장. 섞어 보내면 거절돼야 한다
     */
    private void passRegularMilestone(JsonNode milestone, int foreignCriterionId, Integer clientId, Integer devId)
            throws Exception {
        String base = "/api/milestones/" + id(milestone);
        List<Integer> own = criterionIds(milestone);

        JsonNode submitted = expect(send("POST", base + "/submission", devId, null), 201);
        assertThat(submitted.path("status").asString()).isEqualTo("SUBMITTED");
        // R-QA-02: 확인 요청 뒤 기한이 지나면 자동 통과한다.
        assertThat(OffsetDateTime.parse(submitted.path("autoPassAt").asString()))
                .isEqualTo(OffsetDateTime.parse(submitted.path("submittedAt").asString()).plusDays(3));

        JsonNode partial = expect(send("PUT", base + "/qa-results", clientId,
                Map.of("qaCriterionIds", List.of(own.get(0)))), 200);
        assertThat(partial.path("status").asString()).isEqualTo("IN_PROGRESS");

        expect(send("POST", base + "/submission", devId, null), 201);
        HttpResponse<String> foreign = send("POST", base + "/confirmation", clientId,
                Map.of("qaCriterionIds", List.of(own.get(0), foreignCriterionId)));
        assertThat(foreign.statusCode()).as(foreign.body()).isEqualTo(422);
        assertThat(JSON.readTree(foreign.body()).path("errors").get(0).path("field").asString())
                .isEqualTo("qaCriterionIds");

        JsonNode confirmed = expect(send("POST", base + "/confirmation", clientId,
                Map.of("qaCriterionIds", own)), 201);
        assertThat(confirmed.path("status").asString()).isEqualTo("CONFIRMED");
    }

    private Map<String, Object> contractBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startDate", LocalDate.now().plusDays(1).toString());
        body.put("dueDate", LocalDate.now().plusDays(30).toString());
        body.put("freeRepairDays", 14);
        body.put("deliverableTitles", List.of("소스 파일 전체"));
        body.put("milestones", List.of(
                Map.of("title", "1차", "price", 400_000, "qaCriteria", List.of("로그인이 된다", "로그아웃이 된다")),
                Map.of("title", "2차", "price", 250_000, "qaCriteria", List.of("주문이 저장된다", "주문 목록이 보인다")),
                Map.of("title", "인계", "isHandover", true, "price", 0, "qaCriteria", List.of("소스를 받았다"))));
        return body;
    }

    /** 업로드 3콜로 파일을 올리고 파일 id 를 돌려준다. */
    private long upload(Integer userId, String name, byte[] bytes) throws Exception {
        JsonNode target = expect(send("POST", "/api/uploads", userId, Map.of(
                "originalName", name,
                "contentType", "application/zip",
                "sizeBytes", bytes.length,
                "purpose", "DELIVERABLE")), 201);
        HttpResponse<Void> put = http.send(
                HttpRequest.newBuilder(URI.create(target.path("uploadUrl").asString()))
                        .header("Content-Type", target.path("headers").path("Content-Type").asString())
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(put.statusCode()).isBetween(200, 299);
        return expect(send("POST", "/api/uploads/" + target.path("uploadId").asInt() + "/complete", userId, null), 200)
                .path("fileId").asLong();
    }

    private HttpResponse<byte[]> download(int materialId, Integer userId) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/deliverable-materials/" + materialId + "/file"))
                        .header("Authorization", "Bearer " + jwtTokenProvider.issue(userId))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> send(String method, String path, Integer userId, Object body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Authorization", "Bearer " + jwtTokenProvider.issue(userId))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .method(method, publisher)
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode expect(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return JSON.readTree(response.body());
    }

    private static void expectError(HttpResponse<String> response, int status, String code) {
        assertThat(expect(response, status).path("code").asString()).isEqualTo(code);
    }

    private static JsonNode milestone(JsonNode development, String title) {
        for (JsonNode milestone : development.path("milestones")) {
            if (title.equals(milestone.path("title").asString())) {
                return milestone;
            }
        }
        throw new AssertionError("작업이 없다: " + title);
    }

    private static int id(JsonNode milestone) {
        return milestone.path("milestoneId").asInt();
    }

    private static List<Integer> criterionIds(JsonNode milestone) {
        List<Integer> ids = new ArrayList<>();
        milestone.path("qaCriteria").forEach(criterion -> ids.add(criterion.path("qaCriterionId").asInt()));
        assertThat(ids).isNotEmpty();
        return ids;
    }

    private static int materialId(JsonNode materials, String field, String value) {
        for (JsonNode material : materials) {
            if (value.equals(material.path(field).asString())) {
                return material.path("deliverableMaterialId").asInt();
            }
        }
        throw new AssertionError("인계 자료가 없다: " + field + "=" + value);
    }

    private static User user(long kakaoId, String name, String email) {
        return User.register(kakaoId, name, null, email, true, true, Instant.now(), null, null, Instant.now());
    }

    private static HttpServer startEscrow() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(ESCROW_PREFIX, DevelopmentHandoverFlowIntegrationTest::answerEscrow);
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Escrow 가 답하는 모양을 따른다. 계약 id 는 문자열로 돌려준다 — 두 클라이언트 모두 글자로 견준다. */
    private static void answerEscrow(HttpExchange exchange) throws IOException {
        try {
            String name = exchange.getRequestURI().getPath().substring(ESCROW_PREFIX.length());
            JsonNode request = JSON.readTree(exchange.getRequestBody().readAllBytes());
            Object rec = switch (name) {
                case "createMemberAccount" -> {
                    int n = MEMBER_ACCOUNTS.incrementAndGet();
                    yield Map.of("userKey", "uk-" + n, "bankCode", "001", "accountNo", "00199990000" + n);
                }
                case "createEscrow" -> {
                    String contractId = request.path("contractId").asString();
                    long amount = request.path("amount").asLong();
                    long escrowId = ESCROW_IDS.incrementAndGet();
                    DEPOSITS.put(contractId, amount);
                    ESCROW_CONTRACTS.put(escrowId, contractId);
                    yield Map.of("escrowId", escrowId, "status", "ACTIVE", "depositedAmount", amount, "balance", amount);
                }
                case "inquireEscrowList" -> {
                    List<Map<String, Object>> list = new ArrayList<>();
                    request.path("contractIds").forEach(id -> escrowIdOf(id.asString()).ifPresent(escrowId ->
                            list.add(Map.of(
                                    "escrowId", escrowId,
                                    "contractId", id.asString(),
                                    "status", "ACTIVE",
                                    "balance", balanceOf(id.asString()),
                                    "isHeld", false))));
                    yield Map.of("totalCount", list.size(), "list", list);
                }
                case "inquireEscrow" -> {
                    String contractId = request.path("contractId").asString();
                    yield Map.of(
                            "escrowId", request.path("escrowId").asLong(),
                            "contractId", contractId,
                            "status", "ACTIVE",
                            "isHeld", false,
                            "depositedAmount", DEPOSITS.get(contractId),
                            "balance", balanceOf(contractId),
                            "transactions", transactionsOf(contractId));
                }
                case "releaseEscrow" -> {
                    RELEASE_CALLS.add(request);
                    RELEASED.add(ESCROW_CONTRACTS.get(request.path("escrowId").asLong()));
                    yield Map.of("transactionId", RELEASE_CALLS.size(), "status", "COMPLETED");
                }
                default -> null;
            };
            if (rec == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] payload = JSON.writeValueAsBytes(Map.of("Header", Map.of("responseCode", "H0000"), "REC", rec));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
        } finally {
            exchange.close();
        }
    }

    private static Optional<Long> escrowIdOf(String contractId) {
        return ESCROW_CONTRACTS.entrySet().stream()
                .filter(entry -> entry.getValue().equals(contractId))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private static long balanceOf(String contractId) {
        return RELEASED.contains(contractId) ? 0 : DEPOSITS.getOrDefault(contractId, 0L);
    }

    private static List<Map<String, Object>> transactionsOf(String contractId) {
        List<Map<String, Object>> transactions = new ArrayList<>();
        Map<String, Object> deposit = new LinkedHashMap<>();
        deposit.put("transactionType", "DEPOSIT");
        deposit.put("status", "COMPLETED");
        deposit.put("referenceId", null);
        transactions.add(deposit);
        if (RELEASED.contains(contractId)) {
            String referenceId = "SETTLEMENT:DEVELOPMENT-" + contractId;
            transactions.add(Map.of("transactionType", "RELEASE", "status", "COMPLETED", "referenceId", referenceId));
            transactions.add(Map.of("transactionType", "FEE", "status", "COMPLETED", "referenceId", referenceId));
        }
        return transactions;
    }
}
