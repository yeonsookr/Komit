package com.a501.escrow.domain.escrow.dto;

import java.math.BigInteger;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.a501.escrow.domain.common.Money;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class EscrowSettlementDtos {
    private static final String SETTLEMENT_REFERENCE = "SETTLEMENT:.+";
    private static final String BANK_CODE = "\\d{3}";
    private static final String ACCOUNT_NO = "\\d{16}";

    private EscrowSettlementDtos() {
    }

    public record ReleaseRequest(
            @JsonProperty("Header") @NotNull @Valid EscrowRequestHeader header,
            @Positive long escrowId,
            @NotBlank @Size(max = 100) @Pattern(regexp = SETTLEMENT_REFERENCE) String referenceId,
            @NotNull @Positive BigInteger amount,
            @NotBlank @Pattern(regexp = BANK_CODE) String recipientBankCode,
            @NotBlank @Pattern(regexp = ACCOUNT_NO) String recipientAccountNo,
            @NotBlank @Size(max = 20) String recipientName
    ) {
        public ReleaseRequest {
            requireMoney(amount);
        }
    }

    public record ReleaseRecord(long transactionId, BigInteger releasedAmount, BigInteger feeAmount,
            BigInteger netAmount, BigInteger balance, String status) {
    }

    public record RefundRequest(
            @JsonProperty("Header") @NotNull @Valid EscrowRequestHeader header,
            @Positive long escrowId,
            @NotNull @Positive BigInteger amount,
            @NotBlank @Pattern(regexp = BANK_CODE) String refundBankCode,
            @NotBlank @Pattern(regexp = ACCOUNT_NO) String refundAccountNo,
            @NotBlank @Size(max = 20) String refundHolderName,
            @NotBlank @Size(max = 100) @Pattern(regexp = SETTLEMENT_REFERENCE) String referenceId
    ) {
        public RefundRequest {
            requireMoney(amount);
        }
    }

    public record RefundRecord(long transactionId, BigInteger refundedAmount,
            BigInteger balance, String status) {
    }

    private static void requireMoney(BigInteger amount) {
        if (amount != null && amount.compareTo(Money.MAX_VALUE) > 0) {
            throw new IllegalArgumentException("금액은 NUMERIC(19,0) 범위를 초과할 수 없습니다.");
        }
    }
}
