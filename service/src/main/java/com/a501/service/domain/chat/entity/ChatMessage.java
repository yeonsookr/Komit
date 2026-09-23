package com.a501.service.domain.chat.entity;

import java.time.LocalDateTime;
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
 * <p><b>방을 가리키는 값은 {@code roomId} 입니다.</b> 방 응답의 {@code chatRefId} 는 채팅을 바깥
 * 서비스에 맡길 때를 보고 파 둔 이름표이고, 담긴 정보가 {@code rooms.id} 와 같습니다. 직접 만드는
 * 동안에는 쓰지 않습니다.
 *
 * <p><b>방과 사람을 식별자로만 가리킵니다.</b> 다른 저장소라 외래키가 걸리지 않으므로, 방이 지워져도
 * 메시지는 남습니다. 분쟁이 났을 때 기록을 확인할 수 있어야 하므로 그 편이 맞습니다. (`R-MATCH-06`)
 *
 * <p>수정과 삭제는 두지 않았습니다. 허용 여부가 `RULES.md` 미확정에 남아 있어, 정하기 전에 칸부터
 * 파지 않습니다.
 */
@Getter
@Document(collection = "chat_messages")
@CompoundIndex(name = "room_sent_at_idx", def = "{'roomId': 1, 'sentAt': -1}")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {
    @Id
    private ObjectId id;

    /** 어느 방 대화인지. {@code rooms.id} 다. */
    @Field("roomId")
    private Integer roomId;

    /** 누가 썼는지. {@code users.id} 이며 방 당사자 둘 중 하나다. */
    @Field("senderUserId")
    private Integer senderUserId;

    @Field("content")
    private String content;

    /** 보낸 시각. 목록 정렬과 커서 페이징의 기준이다. */
    @Field("sentAt")
    private LocalDateTime sentAt;

    public static ChatMessage of(
            Integer roomId, Integer senderUserId, String content, LocalDateTime sentAt) {
        ChatMessage message = new ChatMessage();
        message.roomId = Objects.requireNonNull(roomId, "roomId");
        message.senderUserId = Objects.requireNonNull(senderUserId, "senderUserId");
        message.content = Objects.requireNonNull(content, "content");
        message.sentAt = Objects.requireNonNull(sentAt, "sentAt");
        return message;
    }
}
