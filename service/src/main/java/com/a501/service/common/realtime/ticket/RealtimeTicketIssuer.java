package com.a501.service.common.realtime.ticket;

import java.util.EnumMap;
import java.util.Map;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.RealtimeChannelAuthorizer;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class RealtimeTicketIssuer {
    private final Map<RealtimeChannelType, RealtimeChannelAuthorizer> authorizers =
            new EnumMap<>(RealtimeChannelType.class);
    private final RealtimeTicketService ticketService;

    public RealtimeTicketIssuer(
            ObjectProvider<RealtimeChannelAuthorizer> registered, RealtimeTicketService ticketService) {
        registered.forEach(this::register);
        this.ticketService = ticketService;
    }

    private void register(RealtimeChannelAuthorizer authorizer) {
        RealtimeChannelAuthorizer previous = authorizers.put(authorizer.channel(), authorizer);
        if (previous != null) {
            throw new IllegalStateException(
                    "한 채널에 authorizer 는 하나여야 한다: " + authorizer.channel());
        }
    }

    public String issue(Integer userId, RealtimeChannelType channel, String channelId) {
        // 맡은 authorizer 가 없으면 열어 주지 않는다. 검사를 건너뛰는 길을 남기지 않는다.
        RealtimeChannelAuthorizer authorizer = authorizers.get(channel);
        if (authorizer == null) {
            throw new BusinessException(RealtimeErrorCode.CHANNEL_FORBIDDEN);
        }

        authorizer.authorize(userId, channelId);
        return ticketService.issue(new RealtimeTicket(userId, channel, channelId));
    }
}
