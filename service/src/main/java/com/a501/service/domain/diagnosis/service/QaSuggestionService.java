package com.a501.service.domain.diagnosis.service;

import com.a501.service.common.logging.SlowCallThreshold;
import com.a501.service.domain.diagnosis.client.QaSuggestionClient;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 요구사항 한 건에 붙일 확인 문장 후보를 받아 온다. (`R-REQ-01` `R-REQ-04`)
 *
 * <p><b>아무것도 저장하지 않는다.</b> 후보만 돌려주고 확정은 요청서 저장({@code
 * RequirementService.writeCriteria})이 한다. 여기서 저장하면 사용자가 지운 문장이 되살아난다.
 *
 * <p><b>이 클래스에는 {@code @Transactional} 을 걸지 않는다.</b> 조회는
 * {@link IssueRequirementReader} 가 자기 트랜잭션 안에서 끝내고, 이 클래스는 그 결과를 받아
 * 바깥 AI 를 부르기만 한다. 여기에 트랜잭션을 열면 AI 왕복 동안 DB 커넥션을 붙잡는다.
 * (`BACKEND_CONVENTIONS` 16)
 *
 * <p>같은 요구사항으로 다시 불러도 앞의 답과 다르다. 모델이 매번 새로 만들기 때문이며, 고른 것만
 * 남는 구조라 문제가 되지 않는다.
 */
@Service
@RequiredArgsConstructor
// 컨트롤러와 같은 이유다. 이 클래스의 시간은 대부분 AI 응답 대기다.
@SlowCallThreshold("30s")
public class QaSuggestionService {
    private final IssueRequirementReader issueRequirementReader;
    private final QaSuggestionClient qaSuggestionClient;

    public QaSuggestionListResponse suggest(Integer issueId, Integer userId) {
        String requirement = issueRequirementReader.read(issueId, userId);
        return QaSuggestionListResponse.of(qaSuggestionClient.suggest(requirement));
    }
}
