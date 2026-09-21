package com.a501.service.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.presence.RealtimePresence;
import com.a501.service.common.realtime.presence.RealtimePresenceService;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeErrorCode;
import com.a501.service.common.realtime.relay.RealtimeRelayPublisher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class RealtimePeerNoticesTest {
    private static final RealtimePrincipal PRINCIPAL =
            new RealtimePrincipal(12, "conn-1", RealtimeChannelType.CONSULTING_SESSION, "55");

    private final RealtimePresenceService presence = mock(RealtimePresenceService.class);
    private final RealtimeRelayPublisher relayPublisher = mock(RealtimeRelayPublisher.class);
    private final RealtimePeerNotices notices =
            new RealtimePeerNotices(presence, relayPublisher, new ObjectMapper());

    @Test
    void 들어온_것을_보낸_사람만_빼고_알린다() {
        notices.joined(PRINCIPAL);

        RealtimeEnvelope published = captured();
        assertThat(published.type()).isEqualTo("SYSTEM_PEER_JOINED");
        assertThat(published.channel()).isEqualTo(RealtimeChannelType.CONSULTING_SESSION);
        assertThat(published.channelId()).isEqualTo("55");
        assertThat(published.payload().get("userId").asInt()).isEqualTo(12);
    }

    @Test
    void 연결이_하나도_남지_않았으면_나갔다고_알린다() {
        when(presence.inChannel(RealtimeChannelType.CONSULTING_SESSION, "55"))
                .thenReturn(List.of(new RealtimePresence(34, "conn-2")));

        notices.leftIfGone(PRINCIPAL);

        assertThat(captured().type()).isEqualTo("SYSTEM_PEER_LEFT");
    }

    /** 탭을 새로 연 경우다. 연결 식별자는 달라도 사람은 그대로 있다. */
    @Test
    void 같은_사람의_다른_연결이_남아_있으면_알리지_않는다() {
        when(presence.inChannel(RealtimeChannelType.CONSULTING_SESSION, "55"))
                .thenReturn(List.of(new RealtimePresence(12, "conn-2")));

        notices.leftIfGone(PRINCIPAL);

        verify(relayPublisher, never()).publish(any(), anyString());
    }

    /** Redis 가 없으면 알릴 길이 없다. 그 실패로 연결을 닫지는 않는다. */
    @Test
    void 중계가_막혀도_던지지_않는다() {
        doThrow(new BusinessException(RealtimeErrorCode.RELAY_UNAVAILABLE))
                .when(relayPublisher)
                .publish(any(), anyString());

        assertThatCode(() -> notices.joined(PRINCIPAL)).doesNotThrowAnyException();
    }

    private RealtimeEnvelope captured() {
        ArgumentCaptor<RealtimeEnvelope> envelope = ArgumentCaptor.forClass(RealtimeEnvelope.class);
        verify(relayPublisher).publish(envelope.capture(), anyString());
        return envelope.getValue();
    }
}
