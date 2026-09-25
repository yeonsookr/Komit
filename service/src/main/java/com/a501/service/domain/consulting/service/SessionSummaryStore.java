package com.a501.service.domain.consulting.service;

import com.a501.service.domain.consulting.dto.SummaryContent;
import com.a501.service.domain.consulting.dto.request.TranscriptResultRequest;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 요약을 만들 때 쓰는 짧은 조회와 저장.
 *
 * <p><b>생성기와 나눈 이유.</b> 생성기는 트랜잭션 밖에서 AI 를 수십 초 기다린다. 그 사이 DB 커넥션을
 * 붙잡지 않도록 읽기와 쓰기만 여기서 따로 연다. 같은 빈 안에서 자기 {@code @Transactional} 메서드를
 * 부르면 프록시를 지나지 않아 트랜잭션이 걸리지 않으므로 빈을 나눴다. (BACKEND_CONVENTIONS 16)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionSummaryStore {
    private final ConsultingSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    /** 남은 전사의 발화. 전사는 노트북이 올린 본문을 받은 모양 그대로 남긴 것이다. */
    public List<TranscriptResultRequest.Utterance> readUtterances(Integer sessionId) {
        ConsultingSession session = sessionRepository.findByIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> new IllegalStateException("요약할 세션이 없습니다: " + sessionId));
        if (session.getTranscript() == null) {
            throw new IllegalStateException("요약할 전사가 없습니다: " + sessionId);
        }
        return objectMapper.readValue(session.getTranscript(), TranscriptResultRequest.class).utterances();
    }

    /** 요약을 남긴다. 이미 있으면 그대로 둔다. 두 인스턴스가 겹쳐도 행을 잠가 한 번만 남는다. */
    @Transactional
    public boolean save(Integer sessionId, SummaryContent content) {
        return sessionRepository.findWithLockByIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> new IllegalStateException("요약할 세션이 없습니다: " + sessionId))
                .recordSummary(objectMapper.writeValueAsString(content));
    }
}
