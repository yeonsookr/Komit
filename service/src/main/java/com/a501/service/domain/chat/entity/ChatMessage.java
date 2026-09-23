package com.a501.service.domain.chat.entity;

import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * 대화방에서 오간 메시지 한 건. 상담방·컨설팅방·개발방을 가리지 않고 같은 컬렉션에 쌓습니다. (`R-MATCH-06`)
 *
 * <p><b>대화가 붙는 곳이 방만은 아닙니다.</b> 컨설팅 세션에도 붙으므로 {@code chatRefId} 라는 한
 * 이름 공간으로 가리킵니다. 방과 세션 모두 식별자가 1부터 매겨져, 숫자만으로는 어느 쪽 대화인지 가릴 수
 * 없습니다.
 *
 * <p><b>대화와 사람을 식별자로만 가리킵니다.</b> 다른 저장소라 외래키가 걸리지 않으므로, 방이 지워져도
 * 메시지는 남습니다. 분쟁이 났을 때 기록을 확인할 수 있어야 하므로 그 편이 맞습니다. (`R-MATCH-06`)
 *
 * <p>수정과 삭제는 두지 않았습니다. 허용 여부가 `RULES.md` 미확정에 남아 있어, 정하기 전에 칸부터
 * 파지 않습니다.
 */
@Getter
@Document(collection = "chat_messages")
@CompoundIndex(name = "chat_ref_sent_at_idx", def = "{'chatRefId': 1, 'sentAt': -1}")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {
    @Id
    private ObjectId id;

    /**
     * 어느 대화인지. {@code "room-92-chat"} · {@code "session-31-chat"} 처럼 생겼다.
     *
     * <p>방과 세션이 한 이름 공간을 쓴다. 둘 다 식별자가 1부터 매겨져 숫자만으로는 가릴 수 없다.
     */
    @Field("chatRefId")
    private String chatRefId;

    /** 누가 썼는지. {@code users.id} 이며 방 당사자 둘 중 하나다. */
    @Field("senderUserId")
    private Integer senderUserId;

    @Field("content")
    private String content;

    /**
     * 보낸 시각. 목록 정렬과 커서 페이징의 기준이다.
     *
     * <p><b>시간대가 붙은 {@link Instant} 로 둔다.</b> 벽시계 시각으로 두면 읽는 서버의 시간대에
     * 따라 값이 밀리고, 두 사람이 서로 다른 곳에서 보는 대화라 순서가 어긋난다.
     */
    @Field("sentAt")
    private Instant sentAt;

    public static ChatMessage of(
            String chatRefId, Integer senderUserId, String content, Instant sentAt) {
        ChatMessage message = new ChatMessage();
        message.chatRefId = Objects.requireNonNull(chatRefId, "chatRefId");
        message.senderUserId = Objects.requireNonNull(senderUserId, "senderUserId");
        message.content = Objects.requireNonNull(content, "content");
        message.sentAt = Objects.requireNonNull(sentAt, "sentAt");
        return message;
    }
}
