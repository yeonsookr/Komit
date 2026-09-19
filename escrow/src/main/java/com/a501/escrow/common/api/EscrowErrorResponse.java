package com.a501.escrow.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EscrowErrorResponse(@JsonProperty("Header") EscrowResponseHeader header) {
}
