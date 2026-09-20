package com.a501.service.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.presence.RealtimePresenceService;
import com.a501.service.common.realtime.presence.RealtimePresenceService.OwnershipClaimException;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeErrorCode;
import com.a501.service.config.RealtimeProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

class RealtimeWebSocketHandlerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 아래 봉투들은 100바이트 남짓이다. 한도는 그보다 넉넉히 두고 초과 시험만 넘긴다. */
    private static final int MAX_BYTES = 300;
    private static final RealtimePrincipal PRINCIPAL =
            new RealtimePrincipal(12, "conn-1", RealtimeChannelType.CONSULTING_SESSION, "55");

    private RealtimeDispatcher dispatcher;
    private RealtimeConnectionRegistry registry;
    private RealtimePresenceService presence;
    private RealtimeDuplicateConnections duplicates;
    private RealtimeMessageRateLimiter rateLimiter;
    private RealtimeWebSocketHandler handler;
    private WebSocketSession session;
    private final List<String> sent = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        RealtimeProperties properties = RealtimeTestFixtures.properties(MAX_BYTES);
        dispatcher = mock(RealtimeDispatcher.class);
        presence = mock(RealtimePresenceService.class);
        registry = new RealtimeConnectionRegistry(MAPPER, presence, properties);
        duplicates = mock(RealtimeDuplicateConnections.class);
        rateLimiter = mock(RealtimeMessageRateLimiter.class);
        when(rateLimiter.acquire(PRINCIPAL.connectionId()))
                .thenReturn(new RealtimeMessageRateLimiter.Decision(true, false));
        handler =
                new RealtimeWebSocketHandler(
                        dispatcher,
                        registry,
                        presence,
                        duplicates,
                        rateLimiter,
                        MAPPER,
                        properties);

        session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(RealtimeHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, PRINCIPAL);
        when(session.getAttributes()).thenReturn(attributes);
        org.mockito.Mockito.doAnswer(
                        call -> {
                            sent.add(((WebSocketMessage<?>) call.getArgument(0)).getPayload().toString());
                            return null;
                        })
                .when(session)
                .sendMessage(any());

        handler.afterConnectionEstablished(session);
    }

    private void receive(String payload) throws Exception {
        handler.handleTextMessage(session, new TextMessage(payload));
    }

    private JsonNode lastSent() throws Exception {
        return MAPPER.readTree(sent.get(sent.size() - 1));
    }

    private String envelope(String type) {
        return "{\"version\":1,\"type\":\"" + type
                + "\",\"requestId\":\"req-1\",\"channel\":\"CONSULTING_SESSION\",\"channelId\":\"55\"}";
    }

    @Test
    void PING_에는_PONG_으로_답하고_dispatcher_를_부르지_않는다() throws Exception {
        receive(envelope("SYSTEM_PING"));

        assertThat(lastSent().get("type").asText()).isEqualTo("SYSTEM_PONG");
        assertThat(lastSent().get("requestId").asText()).isEqualTo("req-1");
        verify(dispatcher, never()).dispatch(any(), any());
    }

    @Test
    void 도메인_이벤트는_dispatcher_로_넘기고_답장하지_않는다() throws Exception {
        receive(envelope("RTC_SDP_OFFER"));

        ArgumentCaptor<com.a501.service.common.realtime.protocol.RealtimeEnvelope> captured =
                ArgumentCaptor.forClass(com.a501.service.common.realtime.protocol.RealtimeEnvelope.class);
        verify(dispatcher).dispatch(any(), captured.capture());
        assertThat(captured.getValue().type()).isEqualTo("RTC_SDP_OFFER");
        assertThat(sent).isEmpty();
    }

    @Test
    void 깨진_JSON_은_계약_오류로_돌려준다() throws Exception {
        receive("{이건 JSON 이 아니다");

        assertThat(lastSent().get("type").asText()).isEqualTo("SYSTEM_ERROR");
        assertThat(lastSent().get("payload").get("code").asText()).isEqualTo("MALFORMED_ENVELOPE");
    }

    @Test
    void 모르는_channel_값도_계약_오류로_돌려준다() throws Exception {
        // enum 이라 Jackson 이 validated() 전에 막는다. 그 실패도 봉투로 나가야 한다.
        receive("{\"version\":1,\"type\":\"RTC_SDP_OFFER\",\"channel\":\"BOGUS\",\"channelId\":\"55\"}");

        assertThat(lastSent().get("payload").get("code").asText()).isEqualTo("MALFORMED_ENVELOPE");
    }

    @Test
    void 너무_큰_메시지는_거절한다() throws Exception {
        receive("{\"padding\":\"" + "가".repeat(200) + "\"}");

        assertThat(lastSent().get("payload").get("code").asText()).isEqualTo("PAYLOAD_TOO_LARGE");
        verify(dispatcher, never()).dispatch(any(), any());
    }

    @Test
    void 전송_횟수를_넘기면_파싱과_dispatch를_하지_않고_오류로_답한다() throws Exception {
        when(rateLimiter.acquire(PRINCIPAL.connectionId()))
                .thenReturn(new RealtimeMessageRateLimiter.Decision(false, false));

        receive("{파싱할 수 없는 값");

        assertThat(lastSent().get("payload").get("code").asText()).isEqualTo("RATE_LIMITED");
        verify(dispatcher, never()).dispatch(any(), any());
    }

    @Test
    void 전송_제한을_반복해서_넘기면_1008로_닫는다() throws Exception {
        when(rateLimiter.acquire(PRINCIPAL.connectionId()))
                .thenReturn(new RealtimeMessageRateLimiter.Decision(false, true));

        receive(envelope("RTC_SDP_OFFER"));

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        assertThat(lastSent().get("payload").get("code").asText()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void 오류에도_requestId_를_실어_어느_요청인지_잇게_한다() throws Exception {
        doThrow(new BusinessException(RealtimeErrorCode.CHANNEL_FORBIDDEN))
                .when(dispatcher)
                .dispatch(any(), any());

        receive(envelope("RTC_SDP_OFFER"));

        assertThat(lastSent().get("type").asText()).isEqualTo("SYSTEM_ERROR");
        assertThat(lastSent().get("requestId").asText()).isEqualTo("req-1");
        assertThat(lastSent().get("payload").get("code").asText()).isEqualTo("CHANNEL_FORBIDDEN");
    }

    @Test
    void 클라이언트가_보낸_PONG_은_오류가_아니다() throws Exception {
        // 2단계에서 서버가 ping 을 보내면 클라이언트는 PONG 으로 답한다. 그때마다
        // 오류를 돌려주면 heartbeat 마다 오류가 쌓인다.
        receive(envelope("SYSTEM_PONG"));

        assertThat(sent).isEmpty();
        verify(dispatcher, never()).dispatch(any(), any());
    }

    @Test
    void 모르는_SYSTEM_이벤트는_거절한다() throws Exception {
        receive(envelope("SYSTEM_BOGUS"));

        assertThat(lastSent().get("payload").get("code").asText()).isEqualTo("UNKNOWN_EVENT_TYPE");
    }

    @Test
    void 등록되지_않은_연결로_온_메시지는_1001_로_닫는다() throws Exception {
        // 이미 걷어 갔거나 pod 가 내려가는 중이다. 서버 결함이 아니므로 1011 로 알리면
        // 클라이언트가 오류로 알아보고 재접속 대신 오류 화면을 띄운다.
        when(session.getAttributes()).thenReturn(new HashMap<>());

        receive(envelope("SYSTEM_PING"));

        verify(session).close(CloseStatus.GOING_AWAY);
        assertThat(sent).isEmpty();
    }

    @Test
    void 연결이_열리면_registry_에_올린다() {
        assertThat(registry.find(PRINCIPAL.connectionId())).isPresent();
        assertThat(registry.inChannel(RealtimeChannelType.CONSULTING_SESSION, "55")).hasSize(1);
    }

    @Test
    void 연결이_닫히면_registry_에서_내린다() {
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(registry.find(PRINCIPAL.connectionId())).isEmpty();
        assertThat(registry.size()).isZero();
        verify(rateLimiter).remove(PRINCIPAL.connectionId());
    }

    @Test
    void 연결이_열리고_닫히는_것을_presence_에도_알린다() {
        // registry 는 이 pod 것만 안다. 다른 pod 의 상대가 알 수 있는 곳은 presence 뿐이다.
        verify(presence).touch(PRINCIPAL);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(presence).leave(PRINCIPAL);
    }

    @Test
    void 들어오면_같은_사람의_앞선_연결을_정리한다() {
        verify(duplicates).closeOlderOf(PRINCIPAL);
    }

    @Test
    void 대표를_정한_뒤에_presence를_기록한다() {
        InOrder order = inOrder(duplicates, presence);

        order.verify(duplicates).closeOlderOf(PRINCIPAL);
        order.verify(presence).touch(PRINCIPAL);
    }

    @Test
    void 대표_연결을_정할_수_없으면_1013으로_닫는다() throws Exception {
        WebSocketSession retrySession = mock(WebSocketSession.class);
        when(retrySession.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        RealtimePrincipal retryPrincipal =
                new RealtimePrincipal(
                        12, "conn-retry", RealtimeChannelType.CONSULTING_SESSION, "55");
        attributes.put(RealtimeHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, retryPrincipal);
        when(retrySession.getAttributes()).thenReturn(attributes);
        doThrow(new OwnershipClaimException(
                        retryPrincipal,
                        new org.springframework.dao.QueryTimeoutException("redis timeout")))
                .when(duplicates)
                .closeOlderOf(retryPrincipal);

        handler.afterConnectionEstablished(retrySession);

        verify(retrySession).close(CloseStatus.SERVICE_OVERLOAD);
        verify(presence, never()).touch(retryPrincipal);
    }

    @Test
    void principal_이_없으면_정리도_하지_않는다() throws Exception {
        WebSocketSession stray = mock(WebSocketSession.class);
        when(stray.getAttributes()).thenReturn(new HashMap<>());

        handler.afterConnectionEstablished(stray);

        org.mockito.Mockito.verify(duplicates, org.mockito.Mockito.times(1))
                .closeOlderOf(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void principal_이_없으면_registry_에_올리지_않고_닫는다() throws Exception {
        WebSocketSession stray = mock(WebSocketSession.class);
        when(stray.getAttributes()).thenReturn(new HashMap<>());

        handler.afterConnectionEstablished(stray);

        verify(stray).close(CloseStatus.SERVER_ERROR);
        assertThat(registry.size()).isEqualTo(1);
    }
}
