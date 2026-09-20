package com.a501.service.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;
import com.a501.service.common.realtime.ticket.RealtimeTicket;
import com.a501.service.common.realtime.ticket.RealtimeTicketService;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

class RealtimeHandshakeInterceptorTest {
    private static final RealtimeTicket TICKET =
            new RealtimeTicket(12, RealtimeChannelType.CONSULTING_SESSION, "55");

    private RealtimeTicketService ticketService;
    private RealtimeHandshakeInterceptor interceptor;
    private ServerHttpResponse response;
    private final Map<String, Object> attributes = new HashMap<>();

    @BeforeEach
    void setUp() {
        ticketService = mock(RealtimeTicketService.class);
        interceptor = new RealtimeHandshakeInterceptor(ticketService);
        response = mock(ServerHttpResponse.class);
    }

    private ServerHttpRequest requestTo(String uri) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(uri));
        return request;
    }

    private boolean handshake(String uri) {
        return interceptor.beforeHandshake(requestTo(uri), response, null, attributes);
    }

    @Test
    void 티켓이_맞으면_principal_을_붙이고_통과시킨다() {
        when(ticketService.consume("t-1")).thenReturn(Optional.of(TICKET));

        assertThat(handshake("wss://komit.site/service/ws/realtime?ticket=t-1")).isTrue();

        RealtimePrincipal principal =
                (RealtimePrincipal) attributes.get(RealtimeHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
        assertThat(principal.userId()).isEqualTo(12);
        assertThat(principal.channel()).isEqualTo(RealtimeChannelType.CONSULTING_SESSION);
        assertThat(principal.channelId()).isEqualTo("55");
        assertThat(principal.allowedEventFamily()).isEqualTo(RealtimeEventFamily.RTC);
        assertThat(principal.connectionId()).isNotBlank();
    }

    @Test
    void 티켓이_없거나_이미_쓴_것이면_401_로_막는다() {
        when(ticketService.consume(anyString())).thenReturn(Optional.empty());

        assertThat(handshake("wss://komit.site/service/ws/realtime?ticket=t-1")).isFalse();

        org.mockito.Mockito.verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertThat(attributes).isEmpty();
    }

    @Test
    void 티켓_파라미터가_아예_없으면_막는다() {
        when(ticketService.consume(null)).thenReturn(Optional.empty());

        assertThat(handshake("wss://komit.site/service/ws/realtime")).isFalse();
        assertThat(attributes).isEmpty();
    }

    @Test
    void 연결마다_다른_connectionId_를_붙인다() {
        when(ticketService.consume(anyString())).thenReturn(Optional.of(TICKET));

        handshake("wss://komit.site/service/ws/realtime?ticket=t-1");
        String first =
                ((RealtimePrincipal) attributes.get(RealtimeHandshakeInterceptor.PRINCIPAL_ATTRIBUTE))
                        .connectionId();
        attributes.clear();
        handshake("wss://komit.site/service/ws/realtime?ticket=t-2");
        String second =
                ((RealtimePrincipal) attributes.get(RealtimeHandshakeInterceptor.PRINCIPAL_ATTRIBUTE))
                        .connectionId();

        assertThat(first).isNotEqualTo(second);
    }
}
