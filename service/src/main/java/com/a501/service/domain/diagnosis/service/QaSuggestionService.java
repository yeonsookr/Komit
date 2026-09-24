package com.a501.service.domain.diagnosis.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.client.QaSuggestionClient;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionListResponse;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.entity.Issue;
import com.a501.service.domain.diagnosis.repository.InspectionRepository;
import com.a501.service.domain.diagnosis.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요구사항 한 건에 붙일 확인 문장 후보를 받아 온다. (`R-REQ-04`)
 *
 * <p><b>아무것도 저장하지 않습니다.</b> 후보만 돌려주고 확정은 요청서 저장({@code
 * RequirementService.writeCriteria})이 합니다. 여기서 저장하면 사용자가 지운 문장이 되살아납니다.
 *
 * <p>같은 요구사항으로 다시 불러도 앞의 답과 다릅니다. 모델이 매번 새로 만들기 때문이며, 고른 것만
 * 남는 구조라 문제가 되지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QaSuggestionService {
    private final IssueRepository issueRepository;
    private final InspectionRepository inspectionRepository;
    private final DiagnosisOwnership diagnosisOwnership;
    private final QaSuggestionClient qaSuggestionClient;

    /**
     * 확인 문장 후보를 만든다.
     *
     * @throws BusinessException {@link DiagnosisErrorCode#ISSUE_NOT_FOUND} 없거나 남의 요구사항일 때
     */
    public QaSuggestionListResponse suggest(Integer issueId, Integer userId) {
        // 소유권 판정은 IssueQueryService.updateStatus 와 같은 길을 그대로 쓴다. 따로 짜면 같은
        // 요구사항을 두 API 가 다르게 판정할 수 있다.
        Issue issue = issueRepository
                .findByIdAndDeletedFalse(issueId)
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.ISSUE_NOT_FOUND));
        Inspection inspection = inspectionRepository
                .findByIdAndDeletedFalse(issue.getInspectionId())
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.ISSUE_NOT_FOUND));
        diagnosisOwnership
                .findOwned(inspection.getDiagnosisId(), userId)
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.ISSUE_NOT_FOUND));

        return QaSuggestionListResponse.of(qaSuggestionClient.suggest(requirementOf(issue)));
    }

    /**
     * AI 에 넘길 요구사항 문장을 만든다.
     *
     * <p><b>제목만 보내지 않습니다.</b> 모델은 입력에 없는 화면·기능을 지어내지 말라는 지시를 받으므로,
     * 내용을 빼면 쓸 근거가 제목 한 줄뿐이라 뭉뚱그린 문장이 나옵니다. 내용이 비어 있는 결함도
     * 있으므로 그때는 제목만 보냅니다.
     */
    private String requirementOf(Issue issue) {
        String detail = issue.getDetail();
        return detail == null || detail.isBlank()
                ? issue.getTitle()
                : issue.getTitle() + System.lineSeparator() + detail;
    }
}
