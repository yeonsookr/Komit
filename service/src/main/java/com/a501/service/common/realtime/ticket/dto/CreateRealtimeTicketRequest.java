package com.a501.service.common.realtime.ticket.dto;

import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRealtimeTicketRequest(
        @NotNull RealtimeChannelType channel, @NotBlank @Size(max = 50) String channelId) {
}
