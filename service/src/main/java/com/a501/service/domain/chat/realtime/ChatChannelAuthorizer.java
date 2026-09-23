package com.a501.service.domain.chat.realtime;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.RealtimeChannelAuthorizer;
import com.a501.service.common.realtime.protocol.RealtimeChannelType;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화방 입장권을 내줄지 판정합니다.
 *
 * <p>티켓 하나로 연결이 열리므로 판정은 여기서 끝나야 합니다. 소켓이 붙은 뒤에는 다시 묻지 않습니다.
 *
 * <p>방 종류를 가리지 않습니다. 상담방·컨설팅방·개발방의 구조가 같아 채팅이 나눌 이유가 없습니다.
 * (`R-MATCH-04`) 갈래가 정해져 닫힌 방도 들여보냅니다 — 나눈 이야기를 계속 볼 수 있어야 합니다.
 * (`R-MATCH-03`)
 */
@Component
@RequiredArgsConstructor
public class ChatChannelAuthorizer implements RealtimeChannelAuthorizer {
    private final RoomQueryService roomQueryService;

    @Override
    public RealtimeChannelType channel() {
        return RealtimeChannelType.CHAT_ROOM;
    }

    @Override
    @Transactional(readOnly = true)
    public void authorize(Integer userId, String channelId) {
        RoomParties parties = roomQueryService
                .findParties(parseRoomId(channelId))
                .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));

        // 당사자가 아니면 방이 있다는 사실도 알리지 않습니다. (BACKEND_CONVENTIONS 12.1)
        if (!parties.includes(userId)) {
            throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
        }
    }

    /** 채널 식별자는 {@code rooms.id} 다. 방 응답의 {@code chatRefId} 는 여기에 쓰지 않는다. */
    private Integer parseRoomId(String channelId) {
        try {
            return Integer.valueOf(channelId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
        }
    }
}
