package com.a501.service.domain.chat.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.domain.chat.ChatErrorCode;
import com.a501.service.domain.chat.service.ChatMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 대화 티켓을 내줄지 판정하는 자리를 검증합니다. 소켓이 붙은 뒤에는 다시 묻지 않으므로 여기서 끝나야 합니다. */
@ExtendWith(MockitoExtension.class)
class ChatChannelAuthorizerTest {
    private static final Integer CLIENT_ID = 9001;

    @Mock
    private ChatMessageService chatMessageService;

    @InjectMocks
    private ChatChannelAuthorizer authorizer;

    @Test
    @DisplayName("대화 채널을 맡는다")
    void ownsChatRoomChannel() {
        assertThat(authorizer.channel()).isEqualTo(RealtimeChannelType.CHAT_ROOM);
    }

    @Test
    @DisplayName("방 대화와 세션 대화를 같은 채널로 연다")
    void opensBothKindsOfChat() {
        assertThatCode(() -> authorizer.authorize(CLIENT_ID, "room-92-chat"))
                .doesNotThrowAnyException();
        assertThatCode(() -> authorizer.authorize(CLIENT_ID, "session-31-chat"))
                .doesNotThrowAnyException();

        verify(chatMessageService).requireParticipant("room-92-chat", CLIENT_ID);
        verify(chatMessageService).requireParticipant("session-31-chat", CLIENT_ID);
    }

    @Test
    @DisplayName("당사자가 아니면 대화가 있다는 것도 알리지 않는다")
    void hidesChatFromStranger() {
        doThrow(new BusinessException(ChatErrorCode.CHAT_NOT_FOUND))
                .when(chatMessageService)
                .requireParticipant("room-92-chat", CLIENT_ID);

        assertThatThrownBy(() -> authorizer.authorize(CLIENT_ID, "room-92-chat"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ChatErrorCode.CHAT_NOT_FOUND);
    }
}
