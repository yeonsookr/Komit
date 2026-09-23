package com.a501.service.domain.chat.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.chat.dto.response.ChatMessageListResponse;
import com.a501.service.domain.chat.dto.response.ChatMessageResponse;
import com.a501.service.domain.chat.entity.ChatMessage;
import com.a501.service.domain.chat.repository.ChatMessageRepository;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 대화방에서 오간 메시지를 읽고 적는다.
 *
 * <p>방이 상담방인지 컨설팅방인지 개발방인지 가리지 않는다. 셋의 구조가 같아 채팅이 나눌 이유가 없다.
 * (`R-MATCH-04`) 갈래가 정해져 닫힌 방도 들어갈 수 있다. 나눈 이야기를 계속 볼 수 있어야 한다.
 * (`R-MATCH-03`)
 */
@Service
@RequiredArgsConstructor
public class ChatMessageService {
    /** 한 번에 읽을 수 있는 최대 건수. 더 달라고 해도 여기서 끊는다. */
    private static final int MAX_SIZE = 100;

    private final ChatMessageRepository chatMessageRepository;
    private final RoomQueryService roomQueryService;

    /**
     * 이 방의 지난 대화를 오래된 순으로 내려준다.
     *
     * <p>새것부터 읽어 온 뒤 뒤집는다. 화면은 맨 아래가 가장 최근이어야 하는데, 오래된 것부터 읽으면 몇
     * 건인지 먼저 세어야 한다.
     *
     * @param before 마지막으로 본 메시지의 시각. 비우면 가장 최근부터
     * @throws BusinessException {@link RoomErrorCode#ROOM_NOT_FOUND} 없거나 내가 들어가 있지 않은 방일 때
     */
    public ChatMessageListResponse getMessages(
            Integer roomId, Integer userId, OffsetDateTime before, int size) {
        requireParticipant(roomId, userId);

        int limit = Math.clamp(size, 1, MAX_SIZE);
        // 한 건 더 읽어 둔다. 남은 것이 있는지 세지 않고 알아내려는 것이다.
        Limit probe = Limit.of(limit + 1);
        List<ChatMessage> found = before == null
                ? chatMessageRepository.findByRoomIdOrderBySentAtDescIdDesc(roomId, probe)
                : chatMessageRepository.findByRoomIdAndSentAtLessThanOrderBySentAtDescIdDesc(
                        roomId, before.toInstant(), probe);

        boolean hasMore = found.size() > limit;
        List<ChatMessage> page = new ArrayList<>(hasMore ? found.subList(0, limit) : found);

        // 더 위가 있으면 이 묶음에서 가장 오래된 것의 시각을 다음 커서로 준다.
        OffsetDateTime nextBefore = hasMore && !page.isEmpty()
                ? page.getLast().getSentAt().atOffset(ZoneOffset.UTC)
                : null;

        Collections.reverse(page);
        return new ChatMessageListResponse(page.stream().map(ChatMessageResponse::from).toList(), nextBefore);
    }

    /**
     * 메시지를 적는다. 소켓으로 받은 것을 저장할 때 쓴다.
     *
     * @throws BusinessException {@link RoomErrorCode#ROOM_NOT_FOUND} 없거나 내가 들어가 있지 않은 방일 때
     */
    public ChatMessageResponse write(Integer roomId, Integer senderUserId, String content) {
        requireParticipant(roomId, senderUserId);
        return ChatMessageResponse.from(chatMessageRepository.save(
                ChatMessage.of(roomId, senderUserId, content, Instant.now())));
    }

    /** 내가 들어가 있지 않은 방은 없는 것과 같게 다룬다. 존재를 알리지 않는다. (BACKEND_CONVENTIONS 12.1) */
    private void requireParticipant(Integer roomId, Integer userId) {
        RoomParties parties = roomQueryService
                .findParties(roomId)
                .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));
        if (!parties.includes(userId)) {
            throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
        }
    }
}
