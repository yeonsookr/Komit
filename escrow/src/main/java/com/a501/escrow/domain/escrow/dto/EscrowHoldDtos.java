package com.a501.escrow.domain.escrow.dto;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class EscrowHoldDtos {
    private EscrowHoldDtos() {
    }

    public record Request(
            @JsonProperty("Header") @NotNull @Valid EscrowRequestHeader header,
            @Positive long escrowId,
            @NotBlank @Size(max = 100) String reportId
    ) {
    }

    public record Record(long escrowId, boolean isHeld) {
    }
}
