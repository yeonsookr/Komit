package com.a501.service.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.chat.ChatErrorCode;
import com.a501.service.domain.chat.dto.response.ChatMessageListResponse;
import com.a501.service.domain.chat.entity.ChatMessage;
import com.a501.service.domain.chat.repository.ChatMessageRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 대화를 내려주는 규칙을 DB 없이 검증합니다. 뒤집어 내려주는 것, 커서로 끊는 것, 건수 상한, 당사자 판정을
 * 맡은 곳에 넘기는 것이 대상입니다.
 *
 * <p>실제 저장과 정렬은 {@code ChatMessageRepositoryTest} 가 맡습니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatMessageServiceTest {
    private static final String ROOM_CHAT = "room-92-chat";
    private static final String SESSION_CHAT = "session-31-chat";
    private static final Integer CLIENT_ID = 9001;
    private static final Integer STRANGER_ID = 9999;
    private static final Instant BASE = Instant.parse("2026-09-23T14:00:00Z");

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private ChatMessageService chatMessageService;

    /** 방과 세션을 맡는 곳을 하나씩 둡니다. 당사자 판정은 그쪽 몫이므로 여기서는 가짜로 둡니다. */
    private static ChatRoster roster(String prefix, Integer memberUserId) {
        return new ChatRoster() {
            @Override
            public String prefix() {
                return prefix;
            }

            @Override
            public boolean includes(Integer targetId, Integer userId) {
                return memberUserId.equals(userId);
            }
        };
    }

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatRoster> rosters = org.mockito.Mockito.mock(ObjectProvider.class);
        when(rosters.stream())
                .thenAnswer(call -> List.of(roster("room", CLIENT_ID), roster("session", CLIENT_ID))
                        .stream());
        chatMessageService = new ChatMessageService(chatMessageRepository, rosters);
    }

    @Test
    @DisplayName("오래된 것부터 내려준다")
    void returnsOldestFirst() {
        // 저장소는 새것부터 준다. 화면은 맨 아래가 가장 최근이어야 한다.
        givenRecent(message("세 번째", 2), message("두 번째", 1), message("첫 번째", 0));

        ChatMessageListResponse response =
                chatMessageService.getMessages(ROOM_CHAT, CLIENT_ID, null, 30);

        assertThat(response.content()).extracting(item -> item.content())
                .containsExactly("첫 번째", "두 번째", "세 번째");
    }

    @Test
    @DisplayName("더 읽을 것이 없으면 nextBefore 가 비어 있다")
    void noCursorWhenExhausted() {
        givenRecent(message("하나", 0));

        ChatMessageListResponse response =
                chatMessageService.getMessages(ROOM_CHAT, CLIENT_ID, null, 30);

        assertThat(response.nextBefore()).isNull();
    }

    @Test
    @DisplayName("더 위가 있으면 가장 오래된 것의 시각을 다음 커서로 준다")
    void givesCursorWhenMoreRemains() {
        // 2건을 달라고 했는데 3건이 왔다. 한 건 더 읽어 남은 것이 있는지 본다.
        givenRecent(message("셋", 2), message("둘", 1), message("하나", 0));

        ChatMessageListResponse response =
                chatMessageService.getMessages(ROOM_CHAT, CLIENT_ID, null, 2);

        assertThat(response.content()).extracting(item -> item.content())
                .containsExactly("둘", "셋");
        assertThat(response.nextBefore()).isEqualTo(BASE.plusSeconds(60).atOffset(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("before 를 주면 그 시각보다 앞을 읽는다")
    void readsBeforeCursor() {
        OffsetDateTime before = BASE.plusSeconds(120).atOffset(ZoneOffset.UTC);
        when(chatMessageRepository.findByChatRefIdAndSentAtLessThanOrderBySentAtDescIdDesc(
                        eq(ROOM_CHAT), any(), any()))
                .thenReturn(new ArrayList<>(List.of(message("하나", 0))));

        chatMessageService.getMessages(ROOM_CHAT, CLIENT_ID, before, 30);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(chatMessageRepository)
                .findByChatRefIdAndSentAtLessThanOrderBySentAtDescIdDesc(
                        eq(ROOM_CHAT), captor.capture(), any());
        assertThat(captor.getValue()).isEqualTo(BASE.plusSeconds(120));
        verify(chatMessageRepository, never()).findByChatRefIdOrderBySentAtDescIdDesc(any(), any());
    }

    @Test
    @DisplayName("건수가 상한을 넘으면 100 으로 줄인다")
    void clampsSize() {
        givenRecent();

        chatMessageService.getMessages(ROOM_CHAT, CLIENT_ID, null, 5000);

        ArgumentCaptor<Limit> captor = ArgumentCaptor.forClass(Limit.class);
        verify(chatMessageRepository)
                .findByChatRefIdOrderBySentAtDescIdDesc(eq(ROOM_CHAT), captor.capture());
        // 남은 것이 있는지 보려고 한 건 더 읽는다.
        assertThat(captor.getValue().max()).isEqualTo(101);
    }

    @Test
    @DisplayName("세션 대화도 같은 길로 읽는다")
    void readsSessionChatToo() {
        when(chatMessageRepository.findByChatRefIdOrderBySentAtDescIdDesc(eq(SESSION_CHAT), any()))
                .thenReturn(new ArrayList<>(List.of(message("링크 보냅니다", 0))));

        ChatMessageListResponse response =
                chatMessageService.getMessages(SESSION_CHAT, CLIENT_ID, null, 30);

        assertThat(response.content()).hasSize(1);
    }

    @Test
    @DisplayName("들어가 있지 않은 대화는 404 다")
    void hidesChatFromStranger() {
        assertThatThrownBy(() -> chatMessageService.getMessages(ROOM_CHAT, STRANGER_ID, null, 30))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ChatErrorCode.CHAT_NOT_FOUND);
    }

    @Test
    @DisplayName("맡은 곳이 없는 이름표는 404 다")
    void rejectsUnknownPrefix() {
        // 확인을 건너뛰는 길을 남기지 않는다.
        assertThatThrownBy(
                        () -> chatMessageService.getMessages("contract-7-chat", CLIENT_ID, null, 30))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ChatErrorCode.CHAT_NOT_FOUND);
    }

    @Test
    @DisplayName("모양이 틀린 이름표는 404 다")
    void rejectsMalformedReference() {
        // 어떤 이름표가 있는지 더듬어 볼 수 없게 없는 대화와 같게 다룬다.
        assertThatThrownBy(() -> chatMessageService.getMessages("room-92", CLIENT_ID, null, 30))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ChatErrorCode.CHAT_NOT_FOUND);
        assertThatThrownBy(() -> chatMessageService.getMessages("room-abc-chat", CLIENT_ID, null, 30))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ChatErrorCode.CHAT_NOT_FOUND);
    }

    @Test
    @DisplayName("들어가 있지 않은 대화에는 쓸 수 없다")
    void rejectsWriteFromStranger() {
        assertThatThrownBy(() -> chatMessageService.write(ROOM_CHAT, STRANGER_ID, "안녕하세요"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ChatErrorCode.CHAT_NOT_FOUND);
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("당사자는 통과한다")
    void letsParticipantIn() {
        assertThatCode(() -> chatMessageService.requireParticipant(ROOM_CHAT, CLIENT_ID))
                .doesNotThrowAnyException();
    }

    private void givenRecent(ChatMessage... messages) {
        when(chatMessageRepository.findByChatRefIdOrderBySentAtDescIdDesc(eq(ROOM_CHAT), any()))
                .thenReturn(new ArrayList<>(List.of(messages)));
    }

    private static ChatMessage message(String content, int minutesLater) {
        ChatMessage message =
                ChatMessage.of(ROOM_CHAT, CLIENT_ID, content, BASE.plusSeconds(minutesLater * 60L));
        ReflectionTestUtils.setField(message, "id", new ObjectId());
        return message;
    }
}
