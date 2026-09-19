package com.a501.escrow.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.a501.escrow.common.crypto.ClientApiKeyHasher;
import com.a501.escrow.common.exception.EscrowApiException;
import com.a501.escrow.config.SecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InternalApiSecurity {

    private final EscrowHeaderValidator headerValidator;
    private final ServiceClientRepository serviceClientRepository;
    private final ClientApiKeyHasher apiKeyHasher;
    private final WriterGuard writerGuard;
    private final SecurityProperties properties;

    public InternalApiSecurity(
            EscrowHeaderValidator headerValidator,
            ServiceClientRepository serviceClientRepository,
            ClientApiKeyHasher apiKeyHasher,
            WriterGuard writerGuard,
            SecurityProperties properties
    ) {
        this.headerValidator = headerValidator;
        this.serviceClientRepository = serviceClientRepository;
        this.apiKeyHasher = apiKeyHasher;
        this.writerGuard = writerGuard;
        this.properties = properties;
    }

    public AuthenticatedCaller authenticate(
            EscrowRequestHeader header,
            String apiName,
            ApiRequirements requirements
    ) {
        headerValidator.validate(header, apiName, requirements);
        if (requirements.writerRequired()) {
            writerGuard.requireWriter(header);
        }
        if (requirements.operatorOnly()) {
            if (!constantTimeEquals(properties.operatorApiKey(), header.clientApiKey())) {
                invalidApiKey("운영 전용 키가 필요합니다.", header);
            }
            return new AuthenticatedCaller.Operator();
        }
        if (constantTimeEquals(properties.operatorApiKey(), header.clientApiKey())) {
            invalidApiKey("운영 키는 이 API에 사용할 수 없습니다.", header);
        }

        return serviceClientRepository
                .findActiveByApiKeyHash(apiKeyHasher.hash(header.clientApiKey()))
                .<AuthenticatedCaller>map(client ->
                        new AuthenticatedCaller.Client(client.id(), client.name()))
                .orElseThrow(() -> new EscrowApiException(
                        HttpStatus.BAD_REQUEST,
                        "E5001",
                        "CLIENT API KEY가 유효하지 않습니다.",
                        header));
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static void invalidApiKey(String message, EscrowRequestHeader header) {
        throw new EscrowApiException(HttpStatus.BAD_REQUEST, "E5001", message, header);
    }
}
