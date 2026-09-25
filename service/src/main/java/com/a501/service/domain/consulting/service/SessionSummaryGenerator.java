package com.a501.service.domain.consulting.service;

import com.a501.service.common.logging.SlowCallThreshold;
import com.a501.service.domain.consulting.client.ConsultingSummaryClient;
import com.a501.service.domain.consulting.client.SummaryFailedException;
import com.a501.service.domain.consulting.dto.SummaryContent;
import com.a501.service.domain.consulting.dto.request.TranscriptResultRequest;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 전사가 남으면 요약을 만든다.
 *
 * <p><b>전사가 커밋된 뒤, 요청과 다른 스레드에서 돈다.</b> 커밋 전에 돌면 아직 남지 않은 전사를
 * 읽는다. 요청 스레드에서 돌면 노트북이 요약을 기다리다 제 한도(30초)를 넘겨 실패로 본다.
 *
 * <p><b>한 번만 부른다.</b> 실패하면 요약이 빈 채로 남고 화면은 「만드는 중」에 머문다. 다시 만드는
 * 장치는 아직 없다 (`BACKEND_CONVENTIONS` 6.6 의 재시도는 뒤로 미뤘다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionSummaryGenerator {
    private final SessionSummaryStore store;
    private final ConsultingSummaryClient summaryClient;

    // AI 가 GMS 를 기다리는 동안 붙잡힌다. 읽기 한도(app.ai.summary-read-timeout 150s)보다 길게 둔다.
    @SlowCallThreshold("180s")
    @Async
    @TransactionalEventListener
    public void onTranscriptRecorded(TranscriptRecorded event) {
        Integer sessionId = event.sessionId();
        List<TranscriptResultRequest.Utterance> utterances = store.readUtterances(sessionId);

        SummaryContent content;
        if (utterances.isEmpty()) {
            // 두 사람 모두 말하지 않았다. AI 는 빈 발화를 계약 위반으로 거절하므로 부르지 않는다.
            content = SummaryContent.empty();
        } else {
            try {
                content = shape(summaryClient.summarize(utterances));
            } catch (SummaryFailedException failed) {
                log.warn("세션 요약을 만들지 못함: sessionId={} reason={}", sessionId, failed.reason());
                return;
            }
        }
        store.save(sessionId, content);
    }

    /** AI 결과를 화면에 나가는 모양으로 옮긴다. 변경 후보에 1 부터 번호를 붙이고 반영 전으로 둔다. */
    static SummaryContent shape(ConsultingSummaryClient.AiSummary summary) {
        List<ConsultingSummaryClient.AiChange> changes = summary.changes();
        return new SummaryContent(
                items(summary.discussed()),
                items(summary.decided()),
                items(summary.nextActions()),
                IntStream.range(0, changes.size())
                        .mapToObj(index -> {
                            ConsultingSummaryClient.AiChange change = changes.get(index);
                            return new SummaryContent.Change(
                                    index + 1, change.kind(), change.issueId(), change.title(), change.detail(), false);
                        })
                        .toList());
    }

    private static List<SummaryContent.Item> items(List<ConsultingSummaryClient.AiItem> rows) {
        return rows.stream().map(row -> new SummaryContent.Item(row.title(), row.detail())).toList();
    }
}
