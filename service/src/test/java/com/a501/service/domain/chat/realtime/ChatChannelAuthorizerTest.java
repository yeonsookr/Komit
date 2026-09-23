package com.a501.service.domain.chat.realtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 대화방 티켓을 내줄지 판정하는 규칙을 검증합니다. 소켓이 붙은 뒤에는 다시 묻지 않으므로 여기서 끝나야 합니다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatChannelAuthorizerTest {
    private static final Integer CLIENT_ID = 9001;
    private static final Integer DEVELOPER_USER_ID = 9101;
    private static final Integer STRANGER_ID = 9999;

    @Mock
    private RoomQueryService roomQueryService;

    @InjectMocks
    private ChatChannelAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        when(roomQueryService.findParties(92))
                .thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_USER_ID)));
    }

    @Test
    @DisplayName("대화방 채널을 맡는다")
    void ownsChatRoomChannel() {
        assertThatCode(authorizer::channel).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(authorizer.channel())
                .isEqualTo(RealtimeChannelType.CHAT_ROOM);
    }

    @Test
    @DisplayName("의뢰인과 개발자 모두 들여보낸다")
    void letsBothPartiesIn() {
        assertThatCode(() -> authorizer.authorize(CLIENT_ID, "92")).doesNotThrowAnyException();
        assertThatCode(() -> authorizer.authorize(DEVELOPER_USER_ID, "92"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("당사자가 아니면 방이 있다는 것도 알리지 않는다")
    void hidesRoomFromStranger() {
        assertThatThrownBy(() -> authorizer.authorize(STRANGER_ID, "92"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 방은 404 다")
    void rejectsMissingRoom() {
        when(roomQueryService.findParties(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizer.authorize(CLIENT_ID, "404"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("채널 식별자가 숫자가 아니면 404 다")
    void rejectsNonNumericChannelId() {
        // 방 응답의 chatRefId 를 그대로 보내는 경우가 여기에 걸린다.
        assertThatThrownBy(() -> authorizer.authorize(CLIENT_ID, "room-92-chat"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.ROOM_NOT_FOUND);
    }
}
