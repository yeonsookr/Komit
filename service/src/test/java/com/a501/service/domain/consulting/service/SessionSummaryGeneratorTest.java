package com.a501.service.domain.consulting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.a501.service.domain.consulting.client.ConsultingSummaryClient;
import com.a501.service.domain.consulting.client.SummaryFailedException;
import com.a501.service.domain.consulting.dto.Speaker;
import com.a501.service.domain.consulting.dto.SummaryContent;
import com.a501.service.domain.consulting.dto.request.TranscriptResultRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionSummaryGeneratorTest {
    private static final Integer SESSION_ID = 55;
    private static final List<TranscriptResultRequest.Utterance> UTTERANCES = List.of(
            new TranscriptResultRequest.Utterance(0, Speaker.CLIENT, 0, 1200, "로그인이 안 돼요"));

    private final SessionSummaryStore store = mock(SessionSummaryStore.class);
    private final ConsultingSummaryClient client = mock(ConsultingSummaryClient.class);
    private final SessionSummaryGenerator generator = new SessionSummaryGenerator(store, client);

    @Test
    void 변경_후보에_1부터_번호를_붙이고_반영_전으로_둔다() {
        SummaryContent content = SessionSummaryGenerator.shape(new ConsultingSummaryClient.AiSummary(
                List.of(new ConsultingSummaryClient.AiItem("논의", null)),
                List.of(new ConsultingSummaryClient.AiItem("결정", "이유")),
                List.of(),
                List.of(
                        new ConsultingSummaryClient.AiChange(SummaryContent.Kind.ADD, null, "첫째", null),
                        new ConsultingSummaryClient.AiChange(SummaryContent.Kind.ADD, null, "둘째", "설명"))));

        assertThat(content.discussed()).containsExactly(new SummaryContent.Item("논의", null));
        assertThat(content.decided()).containsExactly(new SummaryContent.Item("결정", "이유"));
        assertThat(content.changes()).containsExactly(
                new SummaryContent.Change(1, SummaryContent.Kind.ADD, null, "첫째", null, false),
                new SummaryContent.Change(2, SummaryContent.Kind.ADD, null, "둘째", "설명", false));
    }

    @Test
    void 받은_요약을_남긴다() {
        when(store.readUtterances(SESSION_ID)).thenReturn(UTTERANCES);
        when(client.summarize(UTTERANCES)).thenReturn(new ConsultingSummaryClient.AiSummary(
                List.of(new ConsultingSummaryClient.AiItem("로그인 오류", null)), List.of(), List.of(), List.of()));

        generator.onTranscriptRecorded(new TranscriptRecorded(SESSION_ID));

        verify(store).save(SESSION_ID, new SummaryContent(
                List.of(new SummaryContent.Item("로그인 오류", null)), List.of(), List.of(), List.of()));
    }

    @Test
    void 말이_없던_세션은_AI_를_부르지_않고_빈_요약을_남긴다() {
        when(store.readUtterances(SESSION_ID)).thenReturn(List.of());

        generator.onTranscriptRecorded(new TranscriptRecorded(SESSION_ID));

        verifyNoInteractions(client);
        verify(store).save(SESSION_ID, SummaryContent.empty());
    }

    @Test
    void 요약을_받지_못하면_비워_둔다() {
        when(store.readUtterances(SESSION_ID)).thenReturn(UTTERANCES);
        when(client.summarize(UTTERANCES))
                .thenThrow(new SummaryFailedException(SummaryFailedException.Reason.UNAVAILABLE));

        generator.onTranscriptRecorded(new TranscriptRecorded(SESSION_ID));

        verify(store, never()).save(any(), any());
    }
}
