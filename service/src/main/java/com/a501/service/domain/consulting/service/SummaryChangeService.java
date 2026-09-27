package com.a501.service.domain.consulting.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.consulting.ConsultingErrorCode;
import com.a501.service.domain.consulting.dto.SummaryContent;
import com.a501.service.domain.consulting.dto.response.SessionSummaryResponse;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.contract.dto.ContractParties;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.diagnosis.service.RequirementService;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 요약의 변경 후보를 요청서에 반영한다.
 *
 * <p>요청서에 요구사항을 넣는 일과 요약에 반영 표시를 하는 일을 한 트랜잭션에서 한다. 하나라도 실패하면 둘 다
 * 되돌린다. 요구사항은 진단 도메인이, 반영 표시는 이 도메인이 맡는다. (BACKEND_CONVENTIONS 7.1)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SummaryChangeService {
    private final ConsultingSessionRepository sessionRepository;
    private final ContractQueryService contractQueryService;
    private final RequirementService requirementService;
    private final ObjectMapper objectMapper;

    /**
     * 고른 후보를 요청서에 넣고 바뀐 요약을 돌려준다.
     *
     * <p>세션 행을 잠가 여러 번 눌러도 한 번만 들어간다. 이미 반영했거나 요약에 없는 후보는 건너뛴다. 지금은 새
     * 요구사항({@code ADD})만 반영한다 — AI 가 기존 요구사항을 받지 않아 {@code UPDATE} 후보가 나올 수 없다.
     *
     * @throws BusinessException {@link ConsultingErrorCode#SESSION_NOT_FOUND} — 없거나 지워진 세션, 또는
     *     요청자가 당사자가 아닌 경우
     * @throws BusinessException {@link CommonErrorCode#FORBIDDEN} — 의뢰인이 아닌 경우
     * @throws BusinessException {@link ConsultingErrorCode#SUMMARY_NOT_READY} — 요약이 아직 없을 때
     * @throws BusinessException {@link CommonErrorCode#INVALID_REQUEST} — {@code UPDATE} 후보를 골랐을 때
     */
    @Transactional
    public SessionSummaryResponse apply(Integer userId, Integer sessionId, List<Integer> changeIds) {
        ConsultingSession session = sessionRepository.findWithLockByIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND));
        ContractParties parties = contractQueryService
                .findPartiesByConsultingAgreementId(session.getConsultingAgreementId())
                .filter(found -> found.includes(userId))
                .orElseThrow(() -> new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND));
        // 요청서는 의뢰인의 것이다. 개발자는 읽기만 한다. (R-REQ-05)
        if (!parties.clientId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (session.getSummary() == null) {
            throw new BusinessException(ConsultingErrorCode.SUMMARY_NOT_READY);
        }

        SummaryContent content = objectMapper.readValue(session.getSummary(), SummaryContent.class);
        Set<Integer> wanted = Set.copyOf(changeIds);
        List<SummaryContent.Change> picked = content.changes().stream()
                .filter(change -> wanted.contains(change.changeId()) && !change.isApplied())
                .toList();
        if (picked.stream().anyMatch(change -> change.kind() != SummaryContent.Kind.ADD)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        if (!picked.isEmpty()) {
            Integer projectId = contractQueryService
                    .findProjectIdByConsultingAgreementId(session.getConsultingAgreementId())
                    .orElseThrow(() -> new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND));
            picked.forEach(change -> requirementService.addToRequest(projectId, change.title(), change.detail()));

            content = markApplied(content, picked);
            session.markChangesApplied(objectMapper.writeValueAsString(content));
            // 응답의 updatedAt 을 이번 반영 시각으로 맞춘다.
            sessionRepository.flush();
        }

        return SessionSummaryResponse.of(sessionId, content, session.getUpdatedAt().atOffset(ZoneOffset.UTC));
    }

    private static SummaryContent markApplied(SummaryContent content, List<SummaryContent.Change> picked) {
        Set<Integer> applied = picked.stream().map(SummaryContent.Change::changeId).collect(Collectors.toSet());
        return new SummaryContent(
                content.discussed(),
                content.decided(),
                content.nextActions(),
                content.changes().stream()
                        .map(change -> applied.contains(change.changeId()) ? change.applied() : change)
                        .toList());
    }
}
