package com.a501.service.domain.consulting.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.consulting.ConsultingErrorCode;
import com.a501.service.domain.consulting.dto.SummaryContent;
import com.a501.service.domain.consulting.dto.response.SessionSummaryResponse;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.contract.service.ContractQueryService;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 세션 요약 조회. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionSummaryQueryService {
    private final ConsultingSessionRepository sessionRepository;
    private final ContractQueryService contractQueryService;
    private final ObjectMapper objectMapper;

    /**
     * 세션 요약을 돌려줍니다.
     *
     * <p>요약이 아직 없으면 「만드는 중」입니다. 세션이 끝나지 않았거나, 전사를 기다리거나, 요약을
     * 기다리는 동안이 모두 여기 해당합니다 — 화면은 셋을 가르지 않습니다.
     *
     * @throws BusinessException {@link ConsultingErrorCode#SESSION_NOT_FOUND} — 없거나 지워진 세션,
     *     또는 요청자가 당사자가 아닌 경우
     * @throws BusinessException {@link ConsultingErrorCode#SUMMARY_NOT_READY} — 요약이 아직 없을 때
     */
    public SessionSummaryResponse getSummary(Integer userId, Integer sessionId) {
        ConsultingSession session = sessionRepository.findByIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND));
        boolean party = contractQueryService
                .findPartiesByConsultingAgreementId(session.getConsultingAgreementId())
                .map(parties -> parties.includes(userId))
                .orElse(false);
        if (!party) {
            throw new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND);
        }
        if (session.getSummary() == null) {
            throw new BusinessException(ConsultingErrorCode.SUMMARY_NOT_READY);
        }
        return SessionSummaryResponse.of(
                sessionId,
                objectMapper.readValue(session.getSummary(), SummaryContent.class),
                session.getUpdatedAt().atOffset(ZoneOffset.UTC));
    }
}
