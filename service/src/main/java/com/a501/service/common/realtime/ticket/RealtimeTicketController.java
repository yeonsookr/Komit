package com.a501.service.common.realtime.ticket;

import com.a501.service.common.realtime.ticket.dto.CreateRealtimeTicketRequest;
import com.a501.service.common.realtime.ticket.dto.RealtimeTicketResponse;
import com.a501.service.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Realtime", description = "WebSocket 연결에 쓰는 일회용 티켓 API")
@RestController
@RequestMapping("/api/realtime")
@RequiredArgsConstructor
public class RealtimeTicketController {
    private final RealtimeTicketIssuer ticketIssuer;

    @Operation(
            summary = "연결 티켓 발급",
            description =
                    "WebSocket 을 열 때 쓰는 일회용 티켓을 받는다. 브라우저는 네이티브 WebSocket 에"
                            + " Authorization 헤더를 붙일 수 없고 액세스 토큰은 httpOnly 쿠키라 읽을 수도 없어,"
                            + " 짧은 수명의 티켓으로 대신한다. 한 번 쓰면 바로 폐기된다.")
    @PostMapping("/tickets")
    RealtimeTicketResponse issue(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateRealtimeTicketRequest request) {
        return new RealtimeTicketResponse(
                ticketIssuer.issue(user.userId(), request.channel(), request.channelId()));
    }
}
