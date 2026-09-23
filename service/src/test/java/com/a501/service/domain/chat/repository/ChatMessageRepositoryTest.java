package com.a501.service.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.domain.chat.entity.ChatMessage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Limit;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/** 메시지를 실제 MongoDB 에 넣고 읽습니다. 방으로 가르는 것, 새것부터 오는 것, 커서로 끊는 것이 대상입니다. */
@Testcontainers
@DataMongoTest
class ChatMessageRepositoryTest {
    private static final String CHAT_REF_ID = "room-92-chat";
    private static final String OTHER_CHAT_REF_ID = "session-31-chat";
    private static final Integer CLIENT_ID = 9001;
    private static final Integer DEVELOPER_USER_ID = 9101;
    private static final Instant BASE = Instant.parse("2026-09-23T14:00:00Z");

    @Container
    @ServiceConnection
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:8.0.30-noble");

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
    }

    @Test
    @DisplayName("보낸 메시지를 그대로 읽는다")
    void saveThenRead() {
        chatMessageRepository.save(
                ChatMessage.of(CHAT_REF_ID, CLIENT_ID, "결제가 안 붙었다고 하셨죠", BASE));

        ChatMessage found =
                chatMessageRepository.findByChatRefIdOrderBySentAtDescIdDesc(CHAT_REF_ID, Limit.of(10))
                        .getFirst();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getChatRefId()).isEqualTo(CHAT_REF_ID);
        assertThat(found.getSenderUserId()).isEqualTo(CLIENT_ID);
        assertThat(found.getContent()).isEqualTo("결제가 안 붙었다고 하셨죠");
        assertThat(found.getSentAt()).isEqualTo(BASE);
    }

    @Test
    @DisplayName("새 메시지가 앞에 온다")
    void newestFirst() {
        save(CHAT_REF_ID, "첫 번째", 0);
        save(CHAT_REF_ID, "두 번째", 1);
        save(CHAT_REF_ID, "세 번째", 2);

        List<ChatMessage> found =
                chatMessageRepository.findByChatRefIdOrderBySentAtDescIdDesc(CHAT_REF_ID, Limit.of(10));

        assertThat(found).extracting(ChatMessage::getContent)
                .containsExactly("세 번째", "두 번째", "첫 번째");
    }

    @Test
    @DisplayName("방 대화와 세션 대화는 섞이지 않는다")
    void separatesRooms() {
        save(CHAT_REF_ID, "상담방 이야기", 0);
        save(OTHER_CHAT_REF_ID, "세션 이야기", 1);

        List<ChatMessage> found =
                chatMessageRepository.findByChatRefIdOrderBySentAtDescIdDesc(CHAT_REF_ID, Limit.of(10));

        assertThat(found).extracting(ChatMessage::getContent).containsExactly("상담방 이야기");
    }

    @Test
    @DisplayName("요청한 건수까지만 가져온다")
    void limitsCount() {
        save(CHAT_REF_ID, "1", 0);
        save(CHAT_REF_ID, "2", 1);
        save(CHAT_REF_ID, "3", 2);

        List<ChatMessage> found =
                chatMessageRepository.findByChatRefIdOrderBySentAtDescIdDesc(CHAT_REF_ID, Limit.of(2));

        assertThat(found).extracting(ChatMessage::getContent).containsExactly("3", "2");
    }

    @Test
    @DisplayName("커서보다 앞의 대화만 가져온다")
    void readsBeforeCursor() {
        save(CHAT_REF_ID, "1", 0);
        save(CHAT_REF_ID, "2", 1);
        save(CHAT_REF_ID, "3", 2);

        List<ChatMessage> found =
                chatMessageRepository.findByChatRefIdAndSentAtLessThanOrderBySentAtDescIdDesc(
                        CHAT_REF_ID, BASE.plusSeconds(120), Limit.of(10));

        // 커서와 같은 시각은 이미 본 것이므로 뺍니다.
        assertThat(found).extracting(ChatMessage::getContent).containsExactly("2", "1");
    }

    @Test
    @DisplayName("대화가 없는 방은 빈 목록이다")
    void emptyRoom() {
        assertThat(chatMessageRepository.findByChatRefIdOrderBySentAtDescIdDesc(CHAT_REF_ID, Limit.of(10)))
                .isEmpty();
    }

    private void save(String chatRefId, String content, int minutesLater) {
        chatMessageRepository.save(
                ChatMessage.of(chatRefId, DEVELOPER_USER_ID, content, BASE.plusSeconds(minutesLater * 60L)));
    }
}
