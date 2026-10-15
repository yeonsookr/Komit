package com.a501.service.domain.consulting.service;

import com.a501.service.domain.chat.service.ChatRoster;
import com.a501.service.domain.consulting.entity.ParticipationRecord;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ParticipationRecordRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 컨설팅 세션에 붙은 대화의 당사자를 가려 줍니다.
 *
 * <p>화상 상담 옆에서 링크와 메모를 주고받는 대화입니다. 세션 채팅은 방 대화와 따로 쌓입니다.
 * (`R-MATCH-06`)
 *
 * <p><b>끝난 세션도 들여보냅니다.</b> 통화는 끝났어도 그때 주고받은 것을 다시 볼 수 있어야 합니다. 통화를
 * 트는 채널은 끝난 세션을 막지만(`ConsultingChannelAuthorizer`) 대화는 읽기가 목적이라 다릅니다.
 */
@Component
@RequiredArgsConstructor
public class ConsultingChatRoster implements ChatRoster {
    public static final String PREFIX = "session";

    private final ConsultingSessionRepository sessionRepository;
    private final ParticipationRecordRepository participationRepository;

    @Override
    public String prefix() {
        return PREFIX;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean includes(Integer sessionId, Integer userId) {
        if (sessionRepository.findByIdAndDeletedFalse(sessionId).isEmpty()) {
            return false;
        }
        return participationRepository.findAllByConsultingSessionIdAndDeletedFalse(sessionId).stream()
                .map(ParticipationRecord::getUserId)
                .anyMatch(participant -> Objects.equals(participant, userId));
    }
}
