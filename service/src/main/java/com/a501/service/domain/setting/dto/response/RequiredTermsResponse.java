package com.a501.service.domain.setting.dto.response;

import com.a501.service.domain.setting.entity.Setting;

public record RequiredTermsResponse(
        boolean isEscrowTermsAgreed,
        boolean isPrivacyCollectAgreed,
        boolean isPrivacyThirdPartyAgreed,
        boolean isServiceTermsAgreed) {
    public static RequiredTermsResponse from(Setting setting) {
        return new RequiredTermsResponse(
                setting.isEscrowTermsAgreed(),
                setting.isPrivacyCollectAgreed(),
                setting.isPrivacyThirdPartyAgreed(),
                setting.isServiceTermsAgreed());
    }
}
