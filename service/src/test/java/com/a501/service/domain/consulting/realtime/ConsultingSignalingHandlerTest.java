package com.a501.service.domain.consulting.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.a501.service.common.realtime.RealtimePrincipal;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;
import com.a501.service.common.realtime.relay.RealtimeRelayPublisher;
import org.junit.jupiter.api.Test;

class ConsultingSignalingHandlerTest {
    private final RealtimeRelayPublisher relayPublisher = mock(RealtimeRelayPublisher.class);
    private final ConsultingSignalingHandler handler = new ConsultingSignalingHandler(relayPublisher);

    private static final RealtimePrincipal SENDER =
            new RealtimePrincipal(7, "conn-1", RealtimeChannelType.CONSULTING_SESSION, "42");

    @Test
    void 맡은_계열은_통화다() {
        assertThat(handler.family()).isEqualTo(RealtimeEventFamily.RTC);
    }

    @Test
    void 아는_쪽지만_받는다() {
        assertThat(handler.supports("RTC_OFFER")).isTrue();
        assertThat(handler.supports("RTC_ANSWER")).isTrue();
        assertThat(handler.supports("RTC_ICE_CANDIDATE")).isTrue();
        assertThat(handler.supports("RTC_ICE_RESTART")).isTrue();
        assertThat(handler.supports("RTC_PEER_READY")).isTrue();
        assertThat(handler.supports("RTC_PEER_LEFT")).isTrue();
    }

    @Test
    void 모르는_쪽지는_받지_않는다() {
        assertThat(handler.supports("RTC_SOMETHING_ELSE")).isFalse();
        assertThat(handler.supports("CHAT_MESSAGE")).isFalse();
    }

    /** 보낸 사람에게 되돌아가면 같은 pod 에 있는 사람만 두 번 받는다. */
    @Test
    void 보낸_연결을_빼고_중계한다() {
        RealtimeEnvelope envelope = envelope("RTC_OFFER");

        handler.handle(SENDER, envelope);

        verify(relayPublisher).publish(envelope, "conn-1");
    }

    private RealtimeEnvelope envelope(String type) {
        return new RealtimeEnvelope(
                RealtimeEnvelope.CURRENT_VERSION,
                type,
                "req-1",
                RealtimeChannelType.CONSULTING_SESSION,
                "42",
                null);
    }
}
