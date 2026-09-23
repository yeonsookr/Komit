package com.a501.service.domain.chat.dto.response;

import com.a501.service.domain.chat.entity.ChatMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "대화방에서 오간 메시지 한 건.")
public record ChatMessageResponse(
        @Schema(description = "메시지 식별자.", example = "65f1c2a48b3e4d0012a7f901") String messageId,
        @Schema(
                        description = "보낸 사람의 사용자 식별자. 내 식별자와 같으면 내가 쓴 메시지다.",
                        example = "9001")
                Integer senderUserId,
        @Schema(description = "메시지 본문.", example = "결제가 안 붙었다고 하셨죠") String content,
        @Schema(description = "보낸 시각.") OffsetDateTime sentAt) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId().toHexString(),
                message.getSenderUserId(),
                message.getContent(),
                message.getSentAt().atOffset(ZoneOffset.UTC));
    }
}
