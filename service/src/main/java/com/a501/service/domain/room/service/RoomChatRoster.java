package com.a501.service.domain.room.service;

import com.a501.service.domain.chat.service.ChatRoster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방에 붙은 대화의 당사자를 가려 줍니다.
 *
 * <p>상담방·컨설팅방·개발방을 가리지 않습니다. 셋의 참여자가 같은 방식으로 정해집니다. (`R-MATCH-04`)
 * 갈래가 정해져 닫힌 방도 들여보냅니다 — 나눈 이야기를 계속 볼 수 있어야 합니다. (`R-MATCH-03`)
 */
@Component
@RequiredArgsConstructor
public class RoomChatRoster implements ChatRoster {
    public static final String PREFIX = "room";

    private final RoomQueryService roomQueryService;

    @Override
    public String prefix() {
        return PREFIX;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean includes(Integer roomId, Integer userId) {
        return roomQueryService
                .findParties(roomId)
                .filter(parties -> parties.includes(userId))
                .isPresent();
    }
}
