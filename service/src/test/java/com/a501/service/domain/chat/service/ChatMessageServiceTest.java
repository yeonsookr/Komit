package com.a501.service.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.chat.dto.response.ChatMessageListResponse;
import com.a501.service.domain.chat.entity.ChatMessage;
import com.a501.service.domain.chat.repository.ChatMessageRepository;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 지난 대화를 내려주는 규칙을 DB 없이 검증합니다. 뒤집어 내려주는 것, 커서로 끊는 것, 건수 상한, 404 조건이
 * 대상입니다.
 *
 * <p>실제 저장과 정렬은 {@code ChatMessageRepositoryTest} 가 맡습니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatMessageServiceTest {
    private static final Integer ROOM_ID = 92;
    private static final Integer CLIENT_ID = 9001;
    private static final Integer DEVELOPER_USER_ID = 9101;
    private static final Integer STRANGER_ID = 9999;
    private static final Instant BASE = Instant.parse("2026-09-23T14:00:00Z");

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private RoomQueryService roomQueryService;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        when(roomQueryService.findParties(ROOM_ID))
                .thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_USER_ID)));
    }

    @Test
    @DisplayName("오래된 것부터 내려준다")
    void returnsOldestFirst() {
        // 저장소는 새것부터 준다. 화면은 맨 아래가 가장 최근이어야 한다.
        givenRecent(message("세 번째", 2), message("두 번째", 1), message("첫 번째", 0));

        ChatMessageListResponse response =
                chatMessageService.getMessages(ROOM_ID, CLIENT_ID, null, 30);

        assertThat(response.content()).extracting(item -> item.content())
                .containsExactly("첫 번째", "두 번째", "세 번째");
    }

    @Test
    @DisplayName("더 읽을 것이 없으면 nextBefore 가 비어 있다")
    void noCursorWhenExhausted() {
        givenRecent(message("하나", 0));

        ChatMessageListResponse response =
                chatMessageService.getMessages(ROOM_ID, CLIENT_ID, null, 30);

        assertThat(response.nextBefore()).isNull();
    }

    @Test
    @DisplayName("더 위가 있으면 가장 오래된 것의 시각을 다음 커서로 준다")
    void givesCursorWhenMoreRemains() {
        // 2건을 달라고 했는데 3건이 왔다. 한 건 더 읽어 남은 것이 있는지 본다.
        givenRecent(message("셋", 2), message("둘", 1), message("하나", 0));

        ChatMessageListResponse response =
                chatMessageService.getMessages(ROOM_ID, CLIENT_ID, null, 2);

        assertThat(response.content()).extracting(item -> item.content())
                .containsExactly("둘", "셋");
        assertThat(response.nextBefore()).isEqualTo(BASE.plusSeconds(60).atOffset(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("before 를 주면 그 시각보다 앞을 읽는다")
    void readsBeforeCursor() {
        OffsetDateTime before = BASE.plusSeconds(120).atOffset(ZoneOffset.UTC);
        when(chatMessageRepository.findByRoomIdAndSentAtLessThanOrderBySentAtDescIdDesc(
                        eq(ROOM_ID), any(), any()))
                .thenReturn(new ArrayList<>(List.of(message("하나", 0))));

        chatMessageService.getMessages(ROOM_ID, CLIENT_ID, before, 30);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(chatMessageRepository)
                .findByRoomIdAndSentAtLessThanOrderBySentAtDescIdDesc(
                        eq(ROOM_ID), captor.capture(), any());
        assertThat(captor.getValue()).isEqualTo(BASE.plusSeconds(120));
        verify(chatMessageRepository, never()).findByRoomIdOrderBySentAtDescIdDesc(any(), any());
    }

    @Test
    @DisplayName("건수가 상한을 넘으면 100 으로 줄인다")
    void clampsSize() {
        givenRecent();

        chatMessageService.getMessages(ROOM_ID, CLIENT_ID, null, 5000);

        ArgumentCaptor<Limit> captor = ArgumentCaptor.forClass(Limit.class);
        verify(chatMessageRepository)
                .findByRoomIdOrderBySentAtDescIdDesc(eq(ROOM_ID), captor.capture());
        // 남은 것이 있는지 보려고 한 건 더 읽는다.
        assertThat(captor.getValue().max()).isEqualTo(101);
    }

    @Test
    @DisplayName("개발자도 같은 방을 읽는다")
    void developerReadsSameRoom() {
        givenRecent(message("하나", 0));

        ChatMessageListResponse response =
                chatMessageService.getMessages(ROOM_ID, DEVELOPER_USER_ID, null, 30);

        assertThat(response.content()).hasSize(1);
    }

    @Test
    @DisplayName("들어가 있지 않은 방은 404 다")
    void hidesForeignRoom() {
        assertThatThrownBy(() -> chatMessageService.getMessages(ROOM_ID, STRANGER_ID, null, 30))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 방도 404 다")
    void rejectsMissingRoom() {
        when(roomQueryService.findParties(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.getMessages(404, CLIENT_ID, null, 30))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("들어가 있지 않은 방에는 쓸 수 없다")
    void rejectsWriteToForeignRoom() {
        assertThatThrownBy(() -> chatMessageService.write(ROOM_ID, STRANGER_ID, "안녕하세요"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", RoomErrorCode.ROOM_NOT_FOUND);
        verify(chatMessageRepository, never()).save(any());
    }

    private void givenRecent(ChatMessage... messages) {
        when(chatMessageRepository.findByRoomIdOrderBySentAtDescIdDesc(eq(ROOM_ID), any()))
                .thenReturn(new ArrayList<>(List.of(messages)));
    }

    private static ChatMessage message(String content, int minutesLater) {
        ChatMessage message =
                ChatMessage.of(ROOM_ID, CLIENT_ID, content, BASE.plusSeconds(minutesLater * 60L));
        ReflectionTestUtils.setField(message, "id", new ObjectId());
        return message;
    }
}
