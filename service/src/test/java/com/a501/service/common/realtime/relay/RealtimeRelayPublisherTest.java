package com.a501.service.common.realtime.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeErrorCode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class RealtimeRelayPublisherTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StringRedisTemplate redisTemplate;
    private RealtimeRelayPublisher publisher;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        publisher = new RealtimeRelayPublisher(redisTemplate, MAPPER);
    }

    private RealtimeEnvelope envelope() {
        return new RealtimeEnvelope(
                RealtimeEnvelope.CURRENT_VERSION,
                "RTC_SDP_OFFER",
                "req-1",
                RealtimeChannelType.CONSULTING_SESSION,
                "55",
                null);
    }

    @Test
    void 채널마다_다른_topic_으로_보낸다() {
        publisher.publish(envelope());

        verify(redisTemplate)
                .convertAndSend(
                        org.mockito.ArgumentMatchers.eq("realtime:CONSULTING_SESSION:55"),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 봉투와_건너뛸_연결을_함께_싣는다() throws Exception {
        publisher.publish(envelope(), "conn-1");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate)
                .convertAndSend(org.mockito.ArgumentMatchers.anyString(), body.capture());
        RealtimeRelayMessage sent = MAPPER.readValue(body.getValue(), RealtimeRelayMessage.class);
        assertThat(sent.excludeConnectionId()).isEqualTo("conn-1");
        assertThat(sent.envelope().type()).isEqualTo("RTC_SDP_OFFER");
        assertThat(sent.envelope().channelId()).isEqualTo("55");
    }

    @Test
    void Redis_가_죽으면_보낸_사람이_알_수_있게_실패를_알린다() {
        // 조용히 삼키면 상대에게 닿지 않은 것을 아무도 모른 채 통화가 열리지 않는다.
        doThrow(new RedisConnectionFailureException("down"))
                .when(redisTemplate)
                .convertAndSend(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());

        assertThatThrownBy(() -> publisher.publish(envelope()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(RealtimeErrorCode.RELAY_UNAVAILABLE);
    }
}
