package com.a501.service.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 통화 중계(TURN) 서버 설정.
 *
 * <p><b>공유 비밀은 우리 서버와 중계 서버만 압니다.</b> 화면에는 그것으로 만든 시한부 자격만
 * 나갑니다. 비밀 자체가 나가면 누구나 우리 중계기를 쓸 수 있습니다.
 *
 * @param stunUrls 내 공인 주소를 알아보는 데 쓰는 주소. 자격이 필요 없습니다
 * @param turnUrls 중계 주소. 자격이 필요합니다. 비어 있으면 중계 없이 STUN 만 씁니다
 * @param sharedSecret 자격을 만들 때 쓰는 비밀. `turnUrls` 가 있으면 반드시 있어야 합니다
 * @param credentialTtl 자격 수명 (`R-CON-19`)
 */
@Validated
@ConfigurationProperties(prefix = "app.turn")
public record TurnProperties(
        List<String> stunUrls,
        List<String> turnUrls,
        String sharedSecret,
        @NotNull Duration credentialTtl) {

    public TurnProperties {
        stunUrls = stunUrls == null ? List.of() : List.copyOf(stunUrls);
        turnUrls = turnUrls == null ? List.of() : List.copyOf(turnUrls);
        // 중계 주소를 두고 비밀을 빠뜨리면 자격을 만들지 못해 모든 통화가 직접 연결로만
        // 붙습니다. 조용히 나빠지므로 기동할 때 막습니다.
        if (!turnUrls.isEmpty() && (sharedSecret == null || sharedSecret.isBlank())) {
            throw new IllegalArgumentException("TURN shared secret is required when TURN urls are set");
        }
        if (credentialTtl != null && (credentialTtl.isNegative() || credentialTtl.isZero())) {
            throw new IllegalArgumentException("TURN credential TTL must be positive");
        }
    }

    /** 중계를 쓰지 않는 환경인지. 로컬에 중계 서버를 띄우지 않아도 통화는 되어야 합니다. */
    public boolean relayDisabled() {
        return turnUrls.isEmpty();
    }
}
