package com.a501.escrow.common.crypto;

import com.a501.escrow.config.SecurityProperties;
import org.springframework.stereotype.Component;

@Component
public class ClientApiKeyHasher {

    private final SecurityProperties properties;

    public ClientApiKeyHasher(SecurityProperties properties) {
        this.properties = properties;
    }

    public String hash(String apiKey) {
        return HmacSha256.hex(properties.clientApiKeyHmacSecret(), "client-api-key", apiKey);
    }
}
