package com.a501.service.common.realtime.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.RealtimeChannelAuthorizer;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeErrorCode;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RealtimeTicketIssuerTest {
    private final RealtimeTicketService ticketService = mock(RealtimeTicketService.class);

    /** 등록된 authorizer 를 흉내 낸다. Spring 주입과 같은 자리다. */
    private record Provider<T>(List<T> beans) implements ObjectProvider<T> {
        @Override
        public T getObject() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Stream<T> stream() {
            return beans.stream();
        }
    }

    private RealtimeChannelAuthorizer authorizer(RealtimeChannelType channel) {
        RealtimeChannelAuthorizer authorizer = mock(RealtimeChannelAuthorizer.class);
        when(authorizer.channel()).thenReturn(channel);
        return authorizer;
    }

    private RealtimeTicketIssuer issuer(RealtimeChannelAuthorizer... authorizers) {
        return new RealtimeTicketIssuer(new Provider<>(List.of(authorizers)), ticketService);
    }

    @Test
    void 권한을_통과해야_티켓이_나온다() {
        RealtimeChannelAuthorizer consulting = authorizer(RealtimeChannelType.CONSULTING_SESSION);
        when(ticketService.issue(any())).thenReturn("t-1");

        String ticket = issuer(consulting).issue(12, RealtimeChannelType.CONSULTING_SESSION, "55");

        assertThat(ticket).isEqualTo("t-1");
        verify(consulting).authorize(12, "55");
        verify(ticketService).issue(new RealtimeTicket(12, RealtimeChannelType.CONSULTING_SESSION, "55"));
    }

    @Test
    void 맡은_authorizer_가_없으면_열어주지_않는다() {
        // 검사를 건너뛰는 길을 남기지 않는다. 채팅 authorizer 가 붙기 전까지 채팅 채널은 막힌다.
        assertThatThrownBy(
                        () ->
                                issuer(authorizer(RealtimeChannelType.CONSULTING_SESSION))
                                        .issue(12, RealtimeChannelType.CHAT_ROOM, "room-92-chat"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RealtimeErrorCode.CHANNEL_FORBIDDEN);

        verify(ticketService, never()).issue(any());
    }

    @Test
    void authorizer_가_막으면_티켓을_찍지_않는다() {
        RealtimeChannelAuthorizer consulting = authorizer(RealtimeChannelType.CONSULTING_SESSION);
        org.mockito.Mockito.doThrow(new BusinessException(RealtimeErrorCode.CHANNEL_FORBIDDEN))
                .when(consulting)
                .authorize(12, "55");

        assertThatThrownBy(
                        () -> issuer(consulting).issue(12, RealtimeChannelType.CONSULTING_SESSION, "55"))
                .isInstanceOf(BusinessException.class);

        verify(ticketService, never()).issue(any());
    }

    @Test
    void 한_채널에_authorizer_가_둘이면_기동에서_막는다() {
        // 조용히 하나를 덮으면 어느 검사가 도는지 알 수 없게 된다.
        assertThatThrownBy(
                        () ->
                                issuer(
                                        authorizer(RealtimeChannelType.CONSULTING_SESSION),
                                        authorizer(RealtimeChannelType.CONSULTING_SESSION)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void authorizer_가_하나도_없어도_만들어진다() {
        assertThatThrownBy(
                        () -> issuer().issue(12, RealtimeChannelType.CONSULTING_SESSION, "55"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RealtimeErrorCode.CHANNEL_FORBIDDEN);
    }
}
