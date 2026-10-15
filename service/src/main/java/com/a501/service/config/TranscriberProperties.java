package com.a501.service.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 전사를 받아 가는 노트북의 토큰. (`ai/worker/docs/job-contract.md` 11.2)
 *
 * <p><b>비어 있으면 전사 경로가 모두 닫힙니다.</b> 기동은 막지 않습니다 — 로컬과 CI 는 노트북
 * 없이도 돌아야 하고, 비어 있을 때 열리는 것이 아니라 닫히므로 안전합니다.
 *
 * @param token 노트북이 {@code Authorization: Bearer} 로 싣는 값. 사용자 JWT 가 아닙니다
 */
@ConfigurationProperties(prefix = "app.transcriber")
public record TranscriberProperties(String token) {

    /** 받은 값이 토큰과 같은지. 걸린 시간으로 앞자리가 드러나지 않게 고정 시간으로 비교합니다. */
    public boolean accepts(String presented) {
        if (token == null || token.isBlank() || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }
}
