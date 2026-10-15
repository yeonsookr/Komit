package com.a501.service.common.realtime;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.a501.service.common.realtime.ticket.RealtimeTicket;
import com.a501.service.common.realtime.ticket.RealtimeTicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {
    public static final String PRINCIPAL_ATTRIBUTE = "realtimePrincipal";
    private static final String TICKET_PARAM = "ticket";

    private final RealtimeTicketService ticketService;

    public RealtimeHandshakeInterceptor(RealtimeTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Map<String, Object> attributes) {
        Optional<RealtimeTicket> ticket = ticketService.consume(ticketValue(request));
        if (ticket.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        RealtimeTicket consumed = ticket.get();
        attributes.put(
                PRINCIPAL_ATTRIBUTE,
                new RealtimePrincipal(
                        consumed.userId(),
                        UUID.randomUUID().toString(),
                        consumed.channel(),
                        consumed.channelId()));
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Exception exception) {
        // 남길 것이 없다. 티켓 값은 주소에 실려 오므로 로그에 옮기지 않는다.
    }

    private String ticketValue(ServerHttpRequest request) {
        return UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst(TICKET_PARAM);
    }
}
