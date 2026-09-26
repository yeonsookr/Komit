package com.a501.service.domain.contract.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.ContractDraftInput;
import com.a501.service.domain.contract.dto.ContractDraftMessage;
import com.a501.service.domain.contract.dto.ContractDraftMessage.AuthorRole;
import com.a501.service.domain.diagnosis.dto.DraftRequirements;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ContractDraftClientTest {
    private HttpServer server;
    private volatile int status;
    private volatile String body;
    private volatile String received;
    private volatile String upgrade;

    @BeforeEach
    void startServer() throws IOException {
        status = 200;
        body = """
                {"draft":{"topic":"인증 오류를 함께 확인합니다","detail":"접근 경로와 재현 절차를 확인합니다."}}""";
        received = null;
        upgrade = null;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/contract-draft", exchange -> {
            upgrade = exchange.getRequestHeaders().getFirst("Upgrade");
            received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("컨설팅 초안을 받고 준비된 입력을 그대로 보낸다")
    void generatesConsultingDraft() {
        ContractDraftClient.GeneratedConsultingDraft draft = client().generateConsulting(input());

        assertThat(draft.topic()).isEqualTo("인증 오류를 함께 확인합니다");
        assertThat(draft.detail()).isEqualTo("접근 경로와 재현 절차를 확인합니다.");
        assertThat(draft.contentSnapshot()).contains("\"draft\"").contains("\"topic\"");
        assertThat(received)
                .contains("\"targetType\":\"CONSULTING\"")
                .contains("\"authorRole\":\"CLIENT\"")
                .contains("\"content\":\"관리자 화면에 들어갈 수 없습니다.\"");
    }

    @Test
    @DisplayName("HTTP/1.1 로 보낸다. AI 서버는 h2c 업그레이드를 받으면 본문을 읽지 못한다")
    void sendsWithoutH2cUpgrade() {
        client().generateConsulting(input());

        assertThat(upgrade).isNull();
    }

    @Test
    @DisplayName("detail은 null일 수 있다")
    void acceptsNullDetail() {
        body = """
                {"draft":{"topic":"인증 오류를 확인합니다","detail":null}}""";

        assertThat(client().generateConsulting(input()).detail()).isNull();
    }

    @Test
    @DisplayName("topic은 정확히 255자까지 받을 수 있다")
    void acceptsMaximumTopicLength() {
        body = "{\"draft\":{\"topic\":\"%s\",\"detail\":null}}".formatted("가".repeat(255));

        assertThat(client().generateConsulting(input()).topic()).hasSize(255);
    }

    @Test
    @DisplayName("초안이 없거나 필드가 다르면 502다")
    void rejectsMissingOrMalformedDraft() {
        for (String wrong : List.of(
                "{\"draft\":null}",
                "{\"draft\":{\"topic\":\"주제\"}}",
                "{\"draft\":{\"topic\":\" \",\"detail\":null}}",
                "{\"draft\":{\"topic\":\"주제\",\"detail\":\" \"}}",
                "{\"draft\":{\"topic\":\"%s\",\"detail\":null}}".formatted("가".repeat(256)),
                "{\"draft\":{\"topic\":\"주제\",\"detail\":null,\"price\":1000}}")) {
            body = wrong;
            assertThatThrownBy(() -> client().generateConsulting(input()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.AI_RESPONSE_INVALID);
        }
    }

    @Test
    @DisplayName("AI Worker의 502와 503을 계약 도메인 오류로 옮긴다")
    void mapsWorkerFailures() {
        status = 502;
        body = "{\"code\":\"AI_RESPONSE_INVALID\",\"message\":\"형식 위반\"}";
        assertThatThrownBy(() -> client().generateConsulting(input()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.AI_RESPONSE_INVALID);

        status = 503;
        body = "{\"code\":\"AI_SERVICE_UNAVAILABLE\",\"message\":\"연결 실패\"}";
        assertThatThrownBy(() -> client().generateConsulting(input()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("백엔드가 계약과 다른 요청을 보냈다는 응답은 500이다")
    void requestInvalidMeansInternalError() {
        status = 422;
        body = "{\"code\":\"AI_REQUEST_INVALID\",\"message\":\"요청 형식 위반\"}";

        assertThatThrownBy(() -> client().generateConsulting(input()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("컨설팅 호출에 개발 계약 입력을 넘기지 않는다")
    void rejectsDevelopmentInputBeforeCallingWorker() {
        ContractDraftInput development = new ContractDraftInput("DEVELOPMENT", input().messages());

        assertThatThrownBy(() -> client().generateConsulting(development))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INTERNAL_ERROR);
        assertThat(received).isNull();
    }

    private ContractDraftClient client() {
        return new ContractDraftClient(
                RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5));
    }

    private ContractDraftInput input() {
        return new ContractDraftInput(
                "CONSULTING",
                List.of(new ContractDraftMessage(
                        "inquiry-room-1",
                        AuthorRole.CLIENT,
                        Instant.parse("2026-09-26T01:00:00Z"),
                        "관리자 화면에 들어갈 수 없습니다.")));
    }

    private static final String DEVELOPMENT_BODY = """
            {"draft":{"criteriaAdditions":[{"issueId":312,"qaCriteria":["일반 회원 계정으로는 관리자 화면이 막힌다"]}],
             "newMilestones":[{"title":"관리자 화면에 회원 검색 추가","detail":null,"qaCriteria":[]}]}}""";

    @Test
    @DisplayName("개발 초안은 요청서와 고르지 않은 요구사항을 보내고, 채팅에서 합의한 것만 받는다")
    void generatesDevelopmentDraft() {
        body = DEVELOPMENT_BODY;

        ContractDraftClient.GeneratedDevelopmentDraft draft = client().generateDevelopment(developmentInput());

        assertThat(draft.criteriaAdditions()).containsExactly(new ContractDraftClient.CriteriaAddition(
                312, List.of("일반 회원 계정으로는 관리자 화면이 막힌다")));
        assertThat(draft.newMilestones()).containsExactly(new ContractDraftClient.NewMilestone(
                "관리자 화면에 회원 검색 추가", null, List.of()));
        assertThat(received)
                .contains("\"targetType\":\"DEVELOPMENT\"")
                .contains("\"issues\":[{\"issueId\":312")
                .contains("\"qaCriteria\":[\"로그인 화면이 뜬다\"]")
                .contains("\"rejectedIssues\":[{\"issueId\":313");
    }

    @Test
    @DisplayName("원본 스냅샷에는 AI 응답과 함께 입력으로 보낸 고르지 않은 요구사항을 남긴다")
    void snapshotKeepsTheIssuesLeftOut() {
        body = DEVELOPMENT_BODY;

        String snapshot = client().generateDevelopment(developmentInput()).contentSnapshot();

        assertThat(snapshot).contains("\"draft\"").contains("\"rejectedIssues\"").contains("결제가 가끔 실패한다");
    }

    @Test
    @DisplayName("더할 것이 없으면 빈 초안이다")
    void acceptsEmptyDevelopmentDraft() {
        body = """
                {"draft":{"criteriaAdditions":[],"newMilestones":[]}}""";

        ContractDraftClient.GeneratedDevelopmentDraft draft = client().generateDevelopment(developmentInput());

        assertThat(draft.criteriaAdditions()).isEmpty();
        assertThat(draft.newMilestones()).isEmpty();
    }

    @Test
    @DisplayName("요청서에 없는 요구사항에 확인 문장을 더하거나 모양이 틀리면 계약을 어긴 결과다")
    void rejectsBrokenDevelopmentDraft() {
        List<String> bad = List.of(
                "{\"draft\":{\"criteriaAdditions\":[{\"issueId\":999,\"qaCriteria\":[\"막힌다\"]}],\"newMilestones\":[]}}",
                "{\"draft\":{\"criteriaAdditions\":[{\"issueId\":313,\"qaCriteria\":[\"결제가 된다\"]}],\"newMilestones\":[]}}",
                "{\"draft\":{\"criteriaAdditions\":[{\"issueId\":312,\"qaCriteria\":[]}],\"newMilestones\":[]}}",
                "{\"draft\":{\"criteriaAdditions\":[{\"issueId\":312,\"qaCriteria\":[\"막힌다\"]},"
                        + "{\"issueId\":312,\"qaCriteria\":[\"열린다\"]}],\"newMilestones\":[]}}",
                "{\"draft\":{\"criteriaAdditions\":[{\"issueId\":\"312\",\"qaCriteria\":[\"막힌다\"]}],\"newMilestones\":[]}}",
                "{\"draft\":{\"criteriaAdditions\":[],\"newMilestones\":[{\"title\":\"검색\",\"qaCriteria\":[]}]}}",
                "{\"draft\":{\"criteriaAdditions\":[],\"newMilestones\":[{\"title\":\"%s\",\"detail\":null,\"qaCriteria\":[]}]}}"
                        .formatted("가".repeat(201)),
                "{\"draft\":{\"criteriaAdditions\":[],\"newMilestones\":[{\"title\":\"검색\",\"detail\":\" \",\"qaCriteria\":[]}]}}",
                "{\"draft\":{\"criteriaAdditions\":[],\"newMilestones\":[{\"title\":\"검색\",\"detail\":null,\"qaCriteria\":[\"%s\"]}]}}"
                        .formatted("가".repeat(501)),
                "{\"draft\":{\"criteriaAdditions\":[]}}",
                "{\"draft\":{\"issueIds\":[312],\"milestones\":[],\"rejectedIssues\":[]}}");
        for (String broken : bad) {
            body = broken;
            assertThatThrownBy(() -> client().generateDevelopment(developmentInput()))
                    .as(broken)
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.AI_RESPONSE_INVALID);
        }
    }

    @Test
    @DisplayName("개발 호출에 컨설팅 입력을 넘기지 않는다")
    void rejectsConsultingInputForDevelopment() {
        assertThatThrownBy(() -> client().generateDevelopment(input()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INTERNAL_ERROR);
        assertThat(received).isNull();
    }

    private ContractDraftInput developmentInput() {
        return new ContractDraftInput(
                "DEVELOPMENT",
                input().messages(),
                List.of(new DraftRequirements.Requested(312, "관리자 화면에 인증이 없다", null, List.of("로그인 화면이 뜬다"))),
                List.of(new DraftRequirements.LeftOut(313, "결제가 가끔 실패한다")));
    }
}
