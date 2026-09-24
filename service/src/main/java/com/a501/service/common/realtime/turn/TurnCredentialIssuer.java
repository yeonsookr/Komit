package com.a501.service.common.realtime.turn;

import com.a501.service.config.TurnProperties;
import com.a501.service.common.realtime.turn.dto.IceServer;
import com.a501.service.common.realtime.turn.dto.IceServersResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 중계 서버에 붙을 시한부 자격을 만듭니다. (`R-CON-19`)
 *
 * <p>중계 서버에 계정을 미리 만들어 두지 않습니다. 공유 비밀로 계산한 값을 양쪽이 맞춰 보는
 * 방식이라, 서버는 발급 기록을 남기지 않고 중계 서버는 조회할 곳이 없습니다.
 *
 * <pre>
 * username = &lt;만료 시각(epoch 초)&gt;:&lt;사용자 식별자&gt;
 * password = HMAC-SHA1(공유 비밀, username) 을 Base64
 * </pre>
 *
 * <p><b>발급한 자격은 취소할 수 없습니다.</b> 지울 기록이 없기 때문입니다. 통화가 끝나도
 * 만료 시각까지는 살아 있으므로 수명이 곧 악용 가능한 시간입니다. 그래서 짧게 두고, 통화가
 * 그보다 길어지면 화면이 새로 받아 갈아 끼웁니다.
 *
 * <p>알고리즘이 `HMAC-SHA1` 인 것은 중계 서버 규격이 그렇게 정해져 있어서입니다. 고른 것이
 * 아니므로 더 센 것으로 바꾸려 하지 않습니다.
 */
@Component
public class TurnCredentialIssuer {
    private static final String ALGORITHM = "HmacSHA1";

    private final TurnProperties properties;
    private final Clock clock;

    @Autowired
    public TurnCredentialIssuer(TurnProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /** 시험에서 시각을 고정하려고 둔 생성자입니다. */
    TurnCredentialIssuer(TurnProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 이 사용자가 쓸 ICE 서버 목록을 만듭니다.
     *
     * <p>중계 주소를 두지 않은 환경에서는 STUN 만 돌려줍니다. 통화는 직접 연결로 붙습니다 —
     * 중계 서버를 띄우지 않은 개발 환경에서도 화면이 돌아가야 합니다.
     */
    public IceServersResponse issue(Integer userId) {
        List<IceServer> servers = new ArrayList<>();
        if (!properties.stunUrls().isEmpty()) {
            servers.add(IceServer.withoutCredential(properties.stunUrls()));
        }
        if (properties.relayDisabled()) {
            return new IceServersResponse(List.copyOf(servers), null);
        }

        Instant expiresAt = clock.instant().plus(properties.credentialTtl());
        String username = expiresAt.getEpochSecond() + ":" + userId;
        servers.add(IceServer.withCredential(properties.turnUrls(), username, sign(username)));
        return new IceServersResponse(List.copyOf(servers), expiresAt);
    }

    private String sign(String username) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.sharedSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return Base64.getEncoder()
                    .encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            // 알고리즘은 표준이고 비밀은 기동할 때 검사했으므로 여기 오면 설정이 깨진 것이다.
            throw new IllegalStateException("TURN 자격을 만들지 못했습니다", exception);
        }
    }
}
