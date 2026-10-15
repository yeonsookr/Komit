package com.a501.service.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealtimePropertiesTest {
    private RealtimeProperties properties(Duration heartbeatInterval, Duration heartbeatTimeout) {
        return new RealtimeProperties(
                Duration.ofSeconds(30),
                65536,
                20,
                40,
                3,
                Duration.ofSeconds(10),
                524288,
                heartbeatInterval,
                heartbeatTimeout,
                List.of());
    }

    @Test
    void 기다리는_시간이_묻는_간격보다_짧으면_기동을_막는다() {
        // 답할 틈을 주기 전에 끊어 버려, 멀쩡한 연결이 계속 끊긴다.
        assertThatThrownBy(() -> properties(Duration.ofSeconds(60), Duration.ofSeconds(20)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 간격과_같아도_막는다() {
        assertThatThrownBy(() -> properties(Duration.ofSeconds(20), Duration.ofSeconds(20)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 조금_긴_정도로는_모자라다() {
        // 한 번 도는 데 걸리는 시간과 답이 오는 시간이 있다. 21초로 두면 묻기도 전에 기한이
        // 지나, 멀쩡한 연결이 매번 끊긴다. 최소 두 배는 둬야 한다.
        assertThatThrownBy(() -> properties(Duration.ofSeconds(20), Duration.ofSeconds(21)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 두_배면_받는다() {
        assertThatCode(() -> properties(Duration.ofSeconds(20), Duration.ofSeconds(40)))
                .doesNotThrowAnyException();
    }

    @Test
    void 운영에_쓰는_값을_받는다() {
        assertThatCode(() -> properties(Duration.ofSeconds(20), Duration.ofSeconds(60)))
                .doesNotThrowAnyException();
    }

    @Test
    void 전송_제한이_int_밀리초를_넘으면_막는다() {
        // 감싼 session 은 이 값을 밀리초 int 로 받는다. 그대로 두면 기동이 영문 모를
        // ArithmeticException 으로 죽어 어느 속성 때문인지 알 수 없다.
        assertThatThrownBy(
                        () ->
                                new RealtimeProperties(
                                        Duration.ofSeconds(30),
                                        65536,
                                        20,
                                        40,
                                        3,
                                        Duration.ofDays(30),
                                        524288,
                                        Duration.ofSeconds(20),
                                        Duration.ofSeconds(60),
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 초당_허용량보다_순간_허용량이_작으면_기동을_막는다() {
        assertThatThrownBy(
                        () ->
                                new RealtimeProperties(
                                        Duration.ofSeconds(30),
                                        65536,
                                        20,
                                        19,
                                        3,
                                        Duration.ofSeconds(10),
                                        524288,
                                        Duration.ofSeconds(20),
                                        Duration.ofSeconds(60),
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
