package com.a501.service.domain.chat.realtime;

import com.a501.service.common.realtime.RealtimeChannelAuthorizer;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.domain.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 대화 입장권을 내줄지 판정합니다.
 *
 * <p>티켓 하나로 연결이 열리므로 판정은 여기서 끝나야 합니다. 소켓이 붙은 뒤에는 다시 묻지 않습니다.
 *
 * <p>채널 식별자는 대화 이름표입니다. 방에 붙은 대화와 컨설팅 세션에 붙은 대화가 같은 채널을 쓰므로,
 * 통화를 트는 연결과 세션 채팅 연결은 티켓이 따로 나갑니다.
 */
@Component
@RequiredArgsConstructor
public class ChatChannelAuthorizer implements RealtimeChannelAuthorizer {
    private final ChatMessageService chatMessageService;

    @Override
    public RealtimeChannelType channel() {
        return RealtimeChannelType.CHAT_ROOM;
    }

    @Override
    public void authorize(Integer userId, String channelId) {
        chatMessageService.requireParticipant(channelId, userId);
    }
}
