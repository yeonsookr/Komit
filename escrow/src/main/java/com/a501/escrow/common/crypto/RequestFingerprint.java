package com.a501.escrow.common.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class RequestFingerprint {

    private final ObjectMapper canonicalMapper;

    public RequestFingerprint() {
        this.canonicalMapper = JsonMapper.builder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }

    /** 변경되는 전송 Header를 제외한 업무 본문을 SHA-256 hex로 고정합니다. */
    public String create(Object request) {
        JsonNode tree = canonicalMapper.valueToTree(request);
        if (tree instanceof ObjectNode objectNode) {
            objectNode.remove("Header");
            objectNode.remove("header");
        }
        try {
            byte[] canonicalJson = canonicalMapper.writeValueAsBytes(tree);
            return HexFormat.of().formatHex(sha256(canonicalJson));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("요청 지문을 생성할 수 없습니다.", exception);
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
