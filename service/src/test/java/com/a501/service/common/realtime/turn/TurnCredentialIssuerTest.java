package com.a501.service.common.realtime.turn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a501.service.common.realtime.turn.dto.IceServer;
import com.a501.service.common.realtime.turn.dto.IceServersResponse;
import com.a501.service.config.TurnProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnCredentialIssuerTest {
    private static final Instant NOW = Instant.parse("2026-09-24T09:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SECRET = "local-only-not-a-real-secret";

    /**
     * 수명은 시험용으로 고른 값이다. 실제 값은 `R-CON-19` 에 있고 설정에서 온다 — 여기가
     * 10분인 것은 아래 서명 기댓값과 짝이 맞기 때문일 뿐 규칙과는 무관하다.
     */
    private static final Duration TTL = Duration.ofMinutes(10);

    private static TurnProperties properties(List<String> turnUrls) {
        return new TurnProperties(
                List.of("stun:turn.example:3478"), turnUrls, SECRET, TTL);
    }

    private static IceServer relay(IceServersResponse response) {
        return response.iceServers().stream()
                .filter(server -> server.username() != null)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void 아이디는_만료_시각과_사용자로_만든다() {
        IceServersResponse response =
                new TurnCredentialIssuer(properties(List.of("turn:turn.example:3478")), FIXED)
                        .issue(12);

        // 10분 뒤 = 1790241000. 중계 서버가 이 값을 그대로 읽어 만료를 판단한다.
        assertThat(relay(response).username()).isEqualTo("1790241000:12");
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(TTL));
    }

    /**
     * 파이썬으로 먼저 만들어 실제 coturn 이 받아들인 값과 같아야 한다. 알고리즘이나 인코딩을
     * 잘못 고르면 여기서 갈린다.
     */
    @Test
    void 비밀번호는_아이디를_공유_비밀로_서명한_값이다() {
        IceServersResponse response =
                new TurnCredentialIssuer(properties(List.of("turn:turn.example:3478")), FIXED)
                        .issue(12);

        assertThat(relay(response).credential()).isEqualTo("WEUz+5ZzTKke7d1Emg1mWunaYeo=");
    }

    @Test
    void 중계_주소를_두지_않으면_스턴만_돌려준다() {
        IceServersResponse response =
                new TurnCredentialIssuer(properties(List.of()), FIXED).issue(12);

        assertThat(response.iceServers()).hasSize(1);
        assertThat(response.iceServers().getFirst().username()).isNull();
        // 자격을 낸 적이 없으므로 만료 시각도 없다.
        assertThat(response.expiresAt()).isNull();
    }

    @Test
    void 스턴에는_자격을_싣지_않는다() {
        IceServersResponse response =
                new TurnCredentialIssuer(properties(List.of("turn:turn.example:3478")), FIXED)
                        .issue(12);

        IceServer stun = response.iceServers().getFirst();
        assertThat(stun.urls()).containsExactly("stun:turn.example:3478");
        assertThat(stun.username()).isNull();
        assertThat(stun.credential()).isNull();
    }

    @Test
    void 사람이_다르면_자격도_다르다() {
        TurnCredentialIssuer issuer =
                new TurnCredentialIssuer(properties(List.of("turn:turn.example:3478")), FIXED);

        assertThat(relay(issuer.issue(12)).credential())
                .isNotEqualTo(relay(issuer.issue(13)).credential());
    }

    @Test
    void 중계_주소를_두고_비밀을_빠뜨리면_기동하지_않는다() {
        assertThatThrownBy(
                        () ->
                                new TurnProperties(
                                        List.of(),
                                        List.of("turn:turn.example:3478"),
                                        " ",
                                        TTL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
