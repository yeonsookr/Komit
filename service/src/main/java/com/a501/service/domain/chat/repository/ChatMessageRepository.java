package com.a501.service.domain.chat.repository;

import com.a501.service.domain.chat.entity.ChatMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, ObjectId> {
    /**
     * 이 방의 최근 대화. 새것부터 내려오므로 화면은 뒤집어 그립니다.
     *
     * <p>방에 들어갔을 때 맨 아래가 가장 최근이어야 하는데, 오래된 것부터 읽으면 몇 건인지 먼저
     * 세어야 합니다. 새것부터 읽고 뒤집는 편이 한 번에 끝납니다.
     */
    List<ChatMessage> findByRoomIdOrderBySentAtDescIdDesc(Integer roomId, Limit limit);

    /**
     * 이 시각보다 앞의 대화. 위로 스크롤할 때 씁니다.
     *
     * <p>몇 번째부터 몇 건이 아니라 <b>마지막으로 본 시각보다 앞</b>으로 끊습니다. 읽는 사이에 새
     * 메시지가 와도 경계가 밀리지 않습니다.
     */
    List<ChatMessage> findByRoomIdAndSentAtLessThanOrderBySentAtDescIdDesc(
            Integer roomId, LocalDateTime before, Limit limit);
}
