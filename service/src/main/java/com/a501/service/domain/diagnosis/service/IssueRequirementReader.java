package com.a501.service.domain.diagnosis.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.entity.Issue;
import com.a501.service.domain.diagnosis.repository.InspectionRepository;
import com.a501.service.domain.diagnosis.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 요구사항 한 건을 읽어 AI 에 넘길 문장으로 만든다.
 *
 * <p><b>DB 트랜잭션을 여기서 닫는다.</b> 부르는 쪽이 이 결과를 받은 뒤에 바깥 AI 를 부르므로,
 * 커넥션은 조회가 끝나는 즉시 풀로 돌아간다. AI 왕복을 트랜잭션 안에 두면 {@code app.ai.connect-timeout}
 * 과 {@code app.ai.read-timeout} 을 합한 만큼 커넥션을 붙잡아, 확인 문장 요청 몇 건이 읽기 풀을 비우고
 * 무관한 조회 API 까지 함께 중단된다.
 * (`BACKEND_CONVENTIONS` 16 · 선례 {@code AuthService} {@code AccountVerificationService})
 *
 * <p>엔티티를 밖으로 내보내지 않고 문장까지 만들어 돌려준다. 트랜잭션 밖으로 엔티티가 나가면
 * 지연 로딩이 언제 터질지 모르는 자리가 된다.
 */
@Component
@RequiredArgsConstructor
public class IssueRequirementReader {
    private final IssueRepository issueRepository;
    private final InspectionRepository inspectionRepository;
    private final DiagnosisOwnership diagnosisOwnership;

    /**
     * 요구사항 문장을 읽는다.
     *
     * @throws BusinessException {@link DiagnosisErrorCode#ISSUE_NOT_FOUND} 없거나 남의 요구사항일 때
     */
    @Transactional(readOnly = true)
    public String read(Integer issueId, Integer userId) {
        // 판정 순서를 IssueQueryService.updateStatus 와 같게 맞춘다. 순서가 다르면 같은 요구사항을
        // 두 API 가 다르게 판정한다.
        Issue issue = issueRepository
                .findByIdAndDeletedFalse(issueId)
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.ISSUE_NOT_FOUND));
        Inspection inspection = inspectionRepository
                .findByIdAndDeletedFalse(issue.getInspectionId())
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.ISSUE_NOT_FOUND));
        diagnosisOwnership
                .findOwned(inspection.getDiagnosisId(), userId)
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.ISSUE_NOT_FOUND));
        return requirementOf(issue);
    }

    /**
     * 제목과 내용을 잇는다.
     *
     * <p>제목만 보내지 않는다. 모델은 입력에 없는 화면·기능을 지어내지 말라는 지시를 받으므로,
     * 내용을 빼면 쓸 근거가 제목 한 줄뿐이라 뭉뚱그린 문장이 나온다. 내용이 없는 결함도 있어
     * 그때는 제목만 보낸다.
     */
    private String requirementOf(Issue issue) {
        String detail = issue.getDetail();
        return detail == null || detail.isBlank()
                ? issue.getTitle()
                : issue.getTitle() + System.lineSeparator() + detail;
    }
}
