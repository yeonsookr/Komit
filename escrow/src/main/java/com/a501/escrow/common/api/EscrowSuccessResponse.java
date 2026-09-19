package com.a501.escrow.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EscrowSuccessResponse<T>(
        @JsonProperty("Header") EscrowResponseHeader header,
        @JsonProperty("REC") T record
) {
}
