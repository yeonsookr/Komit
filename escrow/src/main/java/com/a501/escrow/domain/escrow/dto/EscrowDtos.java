package com.a501.escrow.domain.escrow.dto;

import java.math.BigInteger;
import java.util.List;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class EscrowDtos {
    private EscrowDtos() {
    }

    public record CreateRequest(
            @JsonProperty("Header") @NotNull @Valid EscrowRequestHeader header,
            @NotBlank @Size(max = 100) String contractId,
            @NotBlank @Size(max = 30) String contractType,
            @NotBlank @Size(max = 20) String depositorName,
            @NotNull @Positive BigInteger amount,
            @NotBlank @Pattern(regexp = "\\d{3}") String withdrawalBankCode,
            @NotBlank @Pattern(regexp = "\\d{16}") String withdrawalAccountNo
    ) {
    }

    public record AmountRecord(long escrowId, String status, BigInteger depositedAmount,
            BigInteger balance, long transactionId) {
    }

    public record CancelRequest(
            @JsonProperty("Header") @NotNull @Valid EscrowRequestHeader header,
            @Positive long escrowId
    ) {
    }

    public record CancelRecord(long escrowId, String status) {
    }

    public record InquireRequest(
            @JsonProperty("Header") @NotNull @Valid EscrowRequestHeader header,
            @Positive long escrowId,
            @Size(max = 100) String contractId
    ) {
    }

    public record InquireListRequest(
            @JsonProperty("Header") @NotNull @Valid EscrowRequestHeader header,
            @Size(max = 100) List<@NotBlank @Size(max = 100) String> contractIds,
            @Pattern(regexp = "CREATED|DEPOSIT_PENDING|ACTIVE|COMPLETED|REFUNDED|CANCELLED") String status
    ) {
    }

    public record TransactionItem(long transactionId, String transactionType, BigInteger amount,
            String status, String referenceId) {
    }

    public record InquireRecord(long escrowId, String contractId, String status,
            BigInteger depositedAmount, BigInteger releasedAmount, BigInteger feeAmount,
            BigInteger refundedAmount, BigInteger balance, boolean isHeld,
            List<TransactionItem> transactions) {
    }

    public record SummaryItem(long escrowId, String contractId, String status,
            BigInteger balance, boolean isHeld) {
    }

    public record ListRecord(int totalCount, List<SummaryItem> list) {
    }
}
