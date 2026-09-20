package com.a501.escrow.domain.reconciliation.dto;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.List;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class ReconciliationDtos {
    private ReconciliationDtos() {
    }

    public record Request(
            @JsonProperty("Header") @NotNull @Valid EscrowRequestHeader header,
            @Min(1) @Max(30) Integer days
    ) {
        public int requestedDays() {
            return days == null ? 7 : days;
        }
    }

    public record HistoryItem(OffsetDateTime asOf, BigInteger ledgerTotal,
            BigInteger bankBalance, BigInteger differenceAmount, String result) {
    }

    public record Record(BigInteger ledgerTotal, BigInteger bankBalance,
            BigInteger differenceAmount, String result, int escrowCount,
            List<HistoryItem> history) {
    }
}
