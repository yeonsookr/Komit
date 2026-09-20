package com.a501.service.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeErrorCode;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;
import com.a501.service.common.realtime.protocol.RealtimeSystemEvent;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RealtimeDispatcherTest {
    private static final RealtimePrincipal PRINCIPAL =
            new RealtimePrincipal(12, "conn-1", RealtimeChannelType.CONSULTING_SESSION, "55");

    private static final class RecordingHandler implements RealtimeDomainHandler {
        private final RealtimeEventFamily family;
        private RealtimeEnvelope seen;

        private RecordingHandler(RealtimeEventFamily family) {
            this.family = family;
        }

        @Override
        public RealtimeEventFamily family() {
            return family;
        }

        @Override
        public boolean supports(String type) {
            return true;
        }

        @Override
        public void handle(RealtimePrincipal principal, RealtimeEnvelope envelope) {
            this.seen = envelope;
        }
    }

    /** 후보가 하나도 없는 주입을 흉내 낸다. */
    private static final class EmptyProvider<T> implements ObjectProvider<T> {
        @Override
        public T getObject() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Stream<T> stream() {
            return Stream.empty();
        }
    }

    private RealtimeEnvelope envelope(String type, RealtimeChannelType channel, String channelId) {
        return new RealtimeEnvelope(1, type, "req-1", channel, channelId, null);
    }

    @Test
    void 갈래가_맞는_handler_에게만_넘긴다() {
        RecordingHandler rtc = new RecordingHandler(RealtimeEventFamily.RTC);
        RecordingHandler chat = new RecordingHandler(RealtimeEventFamily.CHAT);
        RealtimeDispatcher dispatcher = new RealtimeDispatcher(List.of(rtc, chat));

        dispatcher.dispatch(
                PRINCIPAL, envelope("RTC_SDP_OFFER", RealtimeChannelType.CONSULTING_SESSION, "55"));

        assertThat(rtc.seen.type()).isEqualTo("RTC_SDP_OFFER");
        assertThat(chat.seen).isNull();
    }

    @Test
    void 남의_채널로는_보낼_수_없다() {
        RealtimeDispatcher dispatcher =
                new RealtimeDispatcher(List.of(new RecordingHandler(RealtimeEventFamily.RTC)));

        assertThatThrownBy(
                        () ->
                                dispatcher.dispatch(
                                        PRINCIPAL,
                                        envelope(
                                                "RTC_SDP_OFFER",
                                                RealtimeChannelType.CONSULTING_SESSION,
                                                "999")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RealtimeErrorCode.CHANNEL_FORBIDDEN);
    }

    @Test
    void 다른_갈래의_채널도_막는다() {
        RealtimeDispatcher dispatcher =
                new RealtimeDispatcher(List.of(new RecordingHandler(RealtimeEventFamily.CHAT)));

        assertThatThrownBy(
                        () ->
                                dispatcher.dispatch(
                                        PRINCIPAL,
                                        envelope("CHAT_SEND", RealtimeChannelType.CHAT_ROOM, "55")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RealtimeErrorCode.CHANNEL_FORBIDDEN);
    }

    @Test
    void 같은_채널로_위장해도_티켓이_허용하지_않은_갈래는_막는다() {
        RecordingHandler chat = new RecordingHandler(RealtimeEventFamily.CHAT);
        RealtimeDispatcher dispatcher = new RealtimeDispatcher(List.of(chat));

        assertThatThrownBy(
                        () ->
                                dispatcher.dispatch(
                                        PRINCIPAL,
                                        envelope(
                                                "CHAT_SEND",
                                                RealtimeChannelType.CONSULTING_SESSION,
                                                "55")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RealtimeErrorCode.CHANNEL_FORBIDDEN);
        assertThat(chat.seen).isNull();
    }

    @Test
    void 채팅방_티켓은_RTC_갈래를_허용하지_않는다() {
        RealtimePrincipal chatPrincipal =
                new RealtimePrincipal(12, "conn-1", RealtimeChannelType.CHAT_ROOM, "room-1");
        RecordingHandler rtc = new RecordingHandler(RealtimeEventFamily.RTC);
        RealtimeDispatcher dispatcher = new RealtimeDispatcher(List.of(rtc));

        assertThatThrownBy(
                        () ->
                                dispatcher.dispatch(
                                        chatPrincipal,
                                        envelope(
                                                "RTC_SDP_OFFER",
                                                RealtimeChannelType.CHAT_ROOM,
                                                "room-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RealtimeErrorCode.CHANNEL_FORBIDDEN);
        assertThat(rtc.seen).isNull();
    }

    @Test
    void 맡은_handler_가_없으면_거절한다() {
        RealtimeDispatcher dispatcher = new RealtimeDispatcher(List.of());

        assertThatThrownBy(
                        () ->
                                dispatcher.dispatch(
                                        PRINCIPAL,
                                        envelope(
                                                "RTC_SDP_OFFER",
                                                RealtimeChannelType.CONSULTING_SESSION,
                                                "55")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RealtimeErrorCode.UNKNOWN_EVENT_TYPE);
    }

    @Test
    void SYSTEM_이벤트는_domain_handler_로_내려가지_않는다() {
        RecordingHandler rtc = new RecordingHandler(RealtimeEventFamily.RTC);
        RealtimeDispatcher dispatcher = new RealtimeDispatcher(List.of(rtc));

        // 공통 계층이 소켓 경계에서 가로채야 한다. 여기까지 오면 그 경계가 빠진 것이다.
        assertThatThrownBy(
                        () ->
                                dispatcher.dispatch(
                                        PRINCIPAL,
                                        envelope(
                                                RealtimeSystemEvent.SYSTEM_PING.name(),
                                                RealtimeChannelType.CONSULTING_SESSION,
                                                "55")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(rtc.seen).isNull();
    }

    @Test
    void handler_가_하나도_없어도_만들어진다() {
        // Spring 은 컬렉션 주입에 후보 한 개 이상을 요구한다. ObjectProvider 로 받아
        // handler 가 없는 동안에도 컨텍스트가 뜬다.
        RealtimeDispatcher dispatcher =
                new RealtimeDispatcher(new EmptyProvider<RealtimeDomainHandler>());

        assertThatThrownBy(
                        () ->
                                dispatcher.dispatch(
                                        PRINCIPAL,
                                        envelope(
                                                "RTC_SDP_OFFER",
                                                RealtimeChannelType.CONSULTING_SESSION,
                                                "55")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RealtimeErrorCode.UNKNOWN_EVENT_TYPE);
    }

    @Test
    void 채널_식별자가_비어도_터지지_않는다() {
        RealtimePrincipal noChannelId =
                new RealtimePrincipal(12, "conn-1", RealtimeChannelType.CONSULTING_SESSION, null);
        RealtimeDispatcher dispatcher =
                new RealtimeDispatcher(List.of(new RecordingHandler(RealtimeEventFamily.RTC)));

        assertThatThrownBy(
                        () ->
                                dispatcher.dispatch(
                                        noChannelId,
                                        envelope(
                                                "RTC_SDP_OFFER",
                                                RealtimeChannelType.CONSULTING_SESSION,
                                                "55")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RealtimeErrorCode.CHANNEL_FORBIDDEN);
    }

    @Test
    void 봉투_검사를_먼저_거친다() {
        RealtimeDispatcher dispatcher =
                new RealtimeDispatcher(List.of(new RecordingHandler(RealtimeEventFamily.RTC)));
        RealtimeEnvelope oldVersion =
                new RealtimeEnvelope(
                        99, "RTC_SDP_OFFER", "req-1", RealtimeChannelType.CONSULTING_SESSION, "55", null);

        assertThatThrownBy(() -> dispatcher.dispatch(PRINCIPAL, oldVersion))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RealtimeErrorCode.UNSUPPORTED_PROTOCOL_VERSION);
    }
}
