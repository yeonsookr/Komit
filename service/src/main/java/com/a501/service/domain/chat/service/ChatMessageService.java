package com.a501.service.domain.chat.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.chat.ChatErrorCode;
import com.a501.service.domain.chat.dto.response.ChatMessageListResponse;
import com.a501.service.domain.chat.dto.response.ChatMessageResponse;
import com.a501.service.domain.chat.entity.ChatMessage;
import com.a501.service.domain.chat.repository.ChatMessageRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 대화를 읽고 적습니다.
 *
 * <p>방에 붙은 대화와 컨설팅 세션에 붙은 대화를 같게 다룹니다. (`R-MATCH-06`) 누가 당사자인지는 그쪽
 * 도메인만 아므로 {@link ChatRoster} 에 묻습니다. (BACKEND_CONVENTIONS 7.1)
 */
@Service
public class ChatMessageService {
    /** 한 번에 읽을 수 있는 최대 건수. 더 달라고 해도 여기서 끊습니다. */
    private static final int MAX_SIZE = 100;

    private final ChatMessageRepository chatMessageRepository;
    private final Map<String, ChatRoster> rosters;

    public ChatMessageService(
            ChatMessageRepository chatMessageRepository, ObjectProvider<ChatRoster> rosters) {
        this.chatMessageRepository = chatMessageRepository;
        this.rosters = rosters.stream()
                .collect(Collectors.toMap(ChatRoster::prefix, Function.identity()));
    }

    /**
     * 이 대화의 지난 메시지를 오래된 순으로 내려줍니다.
     *
     * <p>새것부터 읽어 온 뒤 뒤집습니다. 화면은 맨 아래가 가장 최근이어야 하는데, 오래된 것부터 읽으면 몇
     * 건인지 먼저 세어야 합니다.
     *
     * @param before 마지막으로 본 메시지의 시각. 비우면 가장 최근부터
     * @throws BusinessException {@link ChatErrorCode#CHAT_NOT_FOUND} 없거나 내가 들어가 있지 않은 대화일 때
     */
    public ChatMessageListResponse getMessages(
            String chatRefId, Integer userId, OffsetDateTime before, int size) {
        requireParticipant(chatRefId, userId);

        int limit = Math.clamp(size, 1, MAX_SIZE);
        // 한 건 더 읽어 둡니다. 남은 것이 있는지 세지 않고 알아내려는 것입니다.
        Limit probe = Limit.of(limit + 1);
        List<ChatMessage> found = before == null
                ? chatMessageRepository.findByChatRefIdOrderBySentAtDescIdDesc(chatRefId, probe)
                : chatMessageRepository.findByChatRefIdAndSentAtLessThanOrderBySentAtDescIdDesc(
                        chatRefId, before.toInstant(), probe);

        boolean hasMore = found.size() > limit;
        List<ChatMessage> page = new ArrayList<>(hasMore ? found.subList(0, limit) : found);

        // 더 위가 있으면 이 묶음에서 가장 오래된 것의 시각을 다음 커서로 줍니다.
        OffsetDateTime nextBefore = hasMore && !page.isEmpty()
                ? page.getLast().getSentAt().atOffset(ZoneOffset.UTC)
                : null;

        Collections.reverse(page);
        return new ChatMessageListResponse(
                page.stream().map(ChatMessageResponse::from).toList(), nextBefore);
    }

    /**
     * 메시지를 적습니다. 소켓으로 받은 것을 저장할 때 씁니다.
     *
     * @throws BusinessException {@link ChatErrorCode#CHAT_NOT_FOUND} 없거나 내가 들어가 있지 않은 대화일 때
     */
    public ChatMessageResponse write(String chatRefId, Integer senderUserId, String content) {
        requireParticipant(chatRefId, senderUserId);
        return ChatMessageResponse.from(chatMessageRepository.save(
                ChatMessage.of(chatRefId, senderUserId, content, Instant.now())));
    }

    /**
     * 들어갈 수 있는 대화인지 봅니다.
     *
     * <p>들어가 있지 않은 대화는 없는 것과 같게 다룹니다. 존재를 알리지 않습니다.
     * (BACKEND_CONVENTIONS 12.1)
     */
    public void requireParticipant(String chatRefId, Integer userId) {
        ChatReference reference = ChatReference.parse(chatRefId);
        ChatRoster roster = rosters.get(reference.prefix());
        // 맡은 곳이 없으면 열어 주지 않습니다. 확인을 건너뛰는 길을 남기지 않습니다.
        if (roster == null || !roster.includes(reference.targetId(), userId)) {
            throw new BusinessException(ChatErrorCode.CHAT_NOT_FOUND);
        }
    }
}
