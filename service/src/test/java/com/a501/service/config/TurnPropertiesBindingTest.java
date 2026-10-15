package com.a501.service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * 설정값이 실제로 어떻게 묶이는지 본다.
 *
 * <p><b>운영은 이 값들을 환경변수로 받는다.</b> 중계 서버를 두지 않은 환경에서는 그 환경변수가
 * 아예 없고, `application.yml` 의 `${TURN_URLS:}` 가 빈 문자열로 내려온다. 그것이 빈 목록이
 * 아니라 「빈 문자열 하나짜리 목록」으로 묶이면 중계 주소가 있는 것으로 보여 기동이 막힌다.
 *
 * <p>생성자 시험만으로는 이것을 못 잡는다. 거기서는 이미 목록을 만들어 넘기기 때문이다.
 */
class TurnPropertiesBindingTest {
    private static TurnProperties bind(Map<String, Object> source) {
        return new Binder(new MapConfigurationPropertySource(source))
                .bind("app.turn", Bindable.of(TurnProperties.class))
                .orElseThrow(() -> new AssertionError("묶이지 않았다"));
    }

    /** 중계 서버를 두지 않은 환경. 환경변수가 없어 빈 문자열이 내려온다. */
    @Test
    void 주소가_비어_있으면_중계를_쓰지_않는_것으로_묶인다() {
        TurnProperties properties =
                bind(Map.of(
                        "app.turn.stun-urls", "",
                        "app.turn.turn-urls", "",
                        "app.turn.shared-secret", "",
                        "app.turn.credential-ttl", "2h"));

        assertThat(properties.relayDisabled()).isTrue();
        assertThat(properties.turnUrls()).isEmpty();
        assertThat(properties.stunUrls()).isEmpty();
    }

    /** 운영. 한 환경변수에 주소를 쉼표로 이어 넣는다. */
    @Test
    void 쉼표로_이은_주소는_여러_줄로_묶인다() {
        TurnProperties properties =
                bind(Map.of(
                        "app.turn.stun-urls", "stun:komit.site:3478",
                        "app.turn.turn-urls",
                                "turn:komit.site:3478?transport=udp,turn:komit.site:3478?transport=tcp",
                        "app.turn.shared-secret", "not-a-real-secret",
                        "app.turn.credential-ttl", "2h"));

        assertThat(properties.relayDisabled()).isFalse();
        assertThat(properties.turnUrls())
                .containsExactly(
                        "turn:komit.site:3478?transport=udp",
                        "turn:komit.site:3478?transport=tcp");
    }

    /** 주소를 넣고 비밀을 빠뜨리면 조용히 뜨지 않고 기동에서 막힌다. */
    @Test
    void 주소만_넣고_비밀을_빠뜨리면_묶이지_않는다() {
        assertThatThrownBy(
                        () ->
                                bind(Map.of(
                                        "app.turn.turn-urls", "turn:komit.site:3478?transport=udp",
                                        "app.turn.shared-secret", "",
                                        "app.turn.credential-ttl", "2h")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
