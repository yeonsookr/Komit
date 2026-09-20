package com.a501.service.common.realtime.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;
import com.a501.service.config.RealtimeProperties;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RealtimeTicketServiceTest {
    private static final RealtimeTicket TICKET =
            new RealtimeTicket(12, RealtimeChannelType.CONSULTING_SESSION, "55");

    private static final Duration TICKET_TTL = Duration.ofSeconds(30);

    private final Map<String, String> store = new HashMap<>();
    private ValueOperations<String, String> values;
    private RealtimeTicketService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);

        when(values.get(anyString())).thenAnswer(call -> store.get(call.getArgument(0, String.class)));
        when(values.getAndDelete(anyString()))
                .thenAnswer(call -> store.remove(call.getArgument(0, String.class)));
        org.mockito.Mockito.doAnswer(
                        call -> {
                            store.put(call.getArgument(0), call.getArgument(1));
                            return null;
                        })
                .when(values)
                .set(anyString(), anyString(), any(Duration.class));

        service =
                new RealtimeTicketService(
                        redisTemplate,
                        new ObjectMapper(),
                        com.a501.service.common.realtime.RealtimeTestFixtures.properties());
    }

    @Test
    void 발급한_티켓으로_한_번_들어간다() {
        String value = service.issue(TICKET);

        assertThat(service.consume(value)).contains(TICKET);
    }

    @Test
    void 티켓에_채널이_허용하는_이벤트_갈래를_저장한다() {
        String value = service.issue(TICKET);

        assertThat(store.values())
                .singleElement()
                .asString()
                .contains("\"allowedEventFamily\":\"RTC\"");
        assertThat(service.consume(value))
                .get()
                .extracting(RealtimeTicket::allowedEventFamily)
                .isEqualTo(RealtimeEventFamily.RTC);
    }

    @Test
    void 채널과_다른_이벤트_갈래로는_티켓을_만들_수_없다() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new RealtimeTicket(
                                        12,
                                        RealtimeChannelType.CONSULTING_SESSION,
                                        "55",
                                        RealtimeEventFamily.CHAT));
    }

    @Test
    void 같은_티켓을_두_번_쓸_수_없다() {
        String value = service.issue(TICKET);
        service.consume(value);

        assertThat(service.consume(value)).isEmpty();
    }

    @Test
    void 없는_티켓은_거절한다() {
        assertThat(service.consume("아무거나")).isEmpty();
        assertThat(service.consume(null)).isEmpty();
        assertThat(service.consume(" ")).isEmpty();
    }

    @Test
    void 티켓_원본은_저장하지_않는다() {
        String value = service.issue(TICKET);

        // Redis 를 들여다볼 수 있는 사람이 남의 연결을 열지 못해야 한다.
        assertThat(store.keySet()).noneMatch(key -> key.contains(value));
    }

    @Test
    void 발급마다_다른_값이_나온다() {
        assertThat(service.issue(TICKET)).isNotEqualTo(service.issue(TICKET));
    }

    @Test
    void 만료를_설정한_값으로_건다() {
        // TTL 이 빠지면 티켓이 영원히 남는다. 실제로 실린 Duration 을 확인한다.
        service.issue(TICKET);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(anyString(), anyString(), ttl.capture());
        assertThat(ttl.getValue()).isEqualTo(TICKET_TTL);
    }

    @Test
    void 읽을_수_없는_값이_남아_있으면_없는_티켓으로_본다() {
        String value = service.issue(TICKET);
        store.replaceAll((key, old) -> "형식이-바뀐-값");

        Optional<RealtimeTicket> consumed = service.consume(value);

        assertThat(consumed).isEmpty();
        assertThat(store).isEmpty();
    }
}
