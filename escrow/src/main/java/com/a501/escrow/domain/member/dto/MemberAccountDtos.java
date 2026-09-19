package com.a501.escrow.domain.member.dto;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MemberAccountDtos {
    private MemberAccountDtos() {
    }

    public record CreateRequest(
            @JsonProperty("Header") @NotNull @Valid EscrowRequestHeader header,
            @NotBlank @Email @Size(max = 40) String userId
    ) {
    }

    public record CreateRecord(String userId, String userKey, String bankCode, String accountNo) {
    }
}
