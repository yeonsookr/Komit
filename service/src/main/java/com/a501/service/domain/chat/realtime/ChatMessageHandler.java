package com.a501.service.domain.chat.realtime;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.realtime.RealtimeDomainHandler;
import com.a501.service.common.realtime.RealtimePrincipal;
import com.a501.service.common.realtime.protocol.RealtimeEnvelope;
import com.a501.service.common.realtime.protocol.RealtimeErrorCode;
import com.a501.service.common.realtime.protocol.RealtimeEventFamily;
import com.a501.service.common.realtime.relay.RealtimeRelayPublisher;
import com.a501.service.domain.chat.dto.response.ChatMessageResponse;
import com.a501.service.domain.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 올라온 말을 남기고 방에 있는 사람들에게 넘깁니다.
 *
 * <p><b>통화 쪽지와 다른 점은 남긴다는 것 하나입니다.</b> 통화 신호는 지나가면 그만이지만 대화는 이력
 * 복구와 분쟁 확인의 기준이라 저장해야 합니다. (`R-MATCH-06`)
 *
 * <p><b>보낸 사람에게도 돌려줍니다.</b> 저장한 뒤에야 식별자와 확정된 시각이 생기고, 화면은 그것이 와야
 * 「보내는 중」을 「보냄」으로 바꿉니다. 따로 회신을 만들지 않고 같은 것을 함께 받게 해, 모두가 같은 값을
 * 같은 순서로 보게 합니다.
 */
@Component
@RequiredArgsConstructor
public class ChatMessageHandler implements RealtimeDomainHandler {
    private final ChatMessageService chatMessageService;
    private final RealtimeRelayPublisher relayPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public RealtimeEventFamily family() {
        return RealtimeEventFamily.CHAT;
    }

    @Override
    public boolean supports(String type) {
        return ChatEvent.isKnown(type);
    }

    @Override
    public void handle(RealtimePrincipal principal, RealtimeEnvelope envelope) {
        String content = content(envelope);
        ChatMessageResponse saved = chatMessageService.write(
                principal.channelId(), principal.userId(), content);

        // 보낸 사람을 빼지 않는다. 넣는 길이 하나여야 누가 어디에 붙어 있든 같은 순서로 받는다.
        relayPublisher.publish(
                envelope.reply(ChatEvent.CHAT_MESSAGE.name(), objectMapper.valueToTree(saved)));
    }

    /** 본문 길이는 소켓 경계의 프레임 한도가 이미 막습니다. 여기서는 빈 말만 거릅니다. */
    private String content(RealtimeEnvelope envelope) {
        JsonNode payload = envelope.payload();
        if (payload == null || !payload.isObject()) {
            throw new BusinessException(RealtimeErrorCode.MALFORMED_ENVELOPE);
        }
        JsonNode content = payload.get("content");
        if (content == null || !content.isTextual() || content.asString().isBlank()) {
            throw new BusinessException(RealtimeErrorCode.MALFORMED_ENVELOPE);
        }
        if (payload.size() != 1) {
            throw new BusinessException(RealtimeErrorCode.MALFORMED_ENVELOPE);
        }
        return content.asString();
    }
}
