package com.a501.escrow.common.crypto;

import com.a501.escrow.config.SecurityProperties;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataHasher {

    private final SecurityProperties properties;

    public SensitiveDataHasher(SecurityProperties properties) {
        this.properties = properties;
    }

    public String userKeyLookupHash(long serviceClientId, String userKey) {
        return HmacSha256.hex(
                properties.sensitiveDataHmacSecret(),
                "user-key:" + serviceClientId,
                userKey);
    }

    public String accountAuthFingerprint(long serviceClientId, String userKey, String accountNo) {
        return HmacSha256.hex(
                properties.sensitiveDataHmacSecret(),
                "account-auth:" + serviceClientId,
                userKey + "\u0000" + accountNo);
    }
}
