package com.a501.service.domain.setting.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record AgreeRequiredTermsRequest(
        @NotNull @AssertTrue Boolean isEscrowTermsAgreed,
        @NotNull @AssertTrue Boolean isPrivacyCollectAgreed,
        @NotNull @AssertTrue Boolean isPrivacyThirdPartyAgreed,
        @NotNull @AssertTrue Boolean isServiceTermsAgreed) {}
