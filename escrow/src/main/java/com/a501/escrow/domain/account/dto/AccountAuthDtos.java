package com.a501.escrow.domain.account.dto;

import java.util.List;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AccountAuthDtos {

    private AccountAuthDtos() {
    }

    public record OpenRequest(
            @JsonProperty("Header") @NotNull @Valid EscrowRequestHeader header,
            @NotBlank @Pattern(regexp = "\\d{16}") String accountNo
    ) {
    }

    public record OpenRecord(String transactionUniqueNo, String accountNo, String expiresAt) {
    }

    public record CheckRequest(
            @NotBlank @Size(max = 100) String transactionUniqueNo,
            @JsonProperty("Header") @NotNull @Valid EscrowRequestHeader header,
            @NotBlank @Pattern(regexp = "\\d{16}") String accountNo,
            @NotBlank @Pattern(regexp = "\\d{4}") String authCode
    ) {
    }

    public record CheckRecord(String status, String transactionUniqueNo, String accountNo) {
    }

    public record HistoryRequest(
            @JsonProperty("Header") @NotNull @Valid EscrowRequestHeader header,
            @NotBlank @Pattern(regexp = "\\d{16}") String accountNo,
            @NotBlank @Pattern(regexp = "\\d{8}") String startDate,
            @NotBlank @Pattern(regexp = "\\d{8}") String endDate,
            @NotBlank @Pattern(regexp = "M|D|A") String transactionType,
            @Pattern(regexp = "ASC|DESC") String orderByType
    ) {
    }

    public record HistoryItem(
            String transactionUniqueNo,
            String transactionDate,
            String transactionTime,
            String transactionType,
            String transactionTypeName,
            String transactionBalance,
            String transactionAfterBalance,
            String transactionSummary
    ) {
    }

    public record HistoryRecord(int totalCount, List<HistoryItem> list) {
    }
}
