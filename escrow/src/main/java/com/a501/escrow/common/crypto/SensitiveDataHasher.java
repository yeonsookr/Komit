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
}
