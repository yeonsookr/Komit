package com.a501.service.common.realtime.ticket.dto;

import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRealtimeTicketRequest(
        @NotNull RealtimeChannelType channel,
        @NotNull @Size(min = 1, max = 50) @Pattern(regexp = ".*\\S.*") String channelId) {
}
