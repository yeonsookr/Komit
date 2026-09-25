package com.a501.service.domain.consulting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.consulting.ConsultingErrorCode;
import com.a501.service.domain.consulting.dto.Speaker;
import com.a501.service.domain.consulting.dto.request.TranscriptFailureRequest;
import com.a501.service.domain.consulting.dto.request.TranscriptResultRequest;
import com.a501.service.domain.consulting.dto.response.TranscriptionJobResponse;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.entity.ParticipationRecord;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ParticipationRecordRepository;
import com.a501.service.domain.contract.dto.ContractParties;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.file.service.FileLookupService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class TranscriptionJobServiceTest {
    private static final Integer SESSION_ID = 55;
    private static final Integer AGREEMENT_ID = 11;
    private static final Integer CLIENT_ID = 7;
    private static final Integer DEVELOPER_ID = 9;
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    private static final TranscriptResultRequest RESULT = new TranscriptResultRequest(List.of(
            new TranscriptResultRequest.Utterance(0, Speaker.CLIENT, 5680, 6540, "안녕하세요.")));

    private final ConsultingSessionRepository sessionRepository = mock(ConsultingSessionRepository.class);
    private final ParticipationRecordRepository participationRepository =
            mock(ParticipationRecordRepository.class);
    private final ContractQueryService contractQueryService = mock(ContractQueryService.class);
    private final FileLookupService fileLookupService = mock(FileLookupService.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final TranscriptionJobService service = new TranscriptionJobService(
            sessionRepository,
            participationRepository,
            contractQueryService,
            fileLookupService,
            objectMapper,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private ConsultingSession session;

    @BeforeEach
    void setUp() {
        session = BeanUtils.instantiateClass(ConsultingSession.class);
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        ReflectionTestUtils.setField(session, "consultingAgreementId", AGREEMENT_ID);
        when(sessionRepository.findByIdAndDeletedFalse(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.findWithLockByIdAndDeletedFalse(SESSION_ID)).thenReturn(Optional.of(session));
        when(contractQueryService.findPartiesByConsultingAgreementId(AGREEMENT_ID))
                .thenReturn(Optional.of(new ContractParties(120, CLIENT_ID, DEVELOPER_ID)));
    }

    @Test
    void 할_일이_없으면_비어_있다() {
        when(sessionRepository.findNextToTranscribe()).thenReturn(Optional.empty());

        assertThat(service.claim()).isEmpty();
    }

    @Test
    void 녹음을_올린_사람의_역할이_화자가_된다() {
        givenRecordings(Map.of(612, "https://file.example/dev", 611, "https://file.example/client"));

        TranscriptionJobResponse job = service.claim().orElseThrow();

        assertThat(job.sessionId()).isEqualTo(SESSION_ID);
        assertThat(job.tracks()).containsExactly(
                new TranscriptionJobResponse.Track(Speaker.DEVELOPER, "https://file.example/dev"),
                new TranscriptionJobResponse.Track(Speaker.CLIENT, "https://file.example/client"));
    }

    @Test
    void 녹음_주소를_만들지_못하면_한_사람_몫만_넘기지_않는다() {
        givenRecordings(Map.of(612, "https://file.example/dev"));

        assertThatThrownBy(service::claim).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 결과를_받은_모양_그대로_남긴다() {
        service.recordResult(SESSION_ID, RESULT);

        assertThat(objectMapper.readTree(session.getTranscript()))
                .isEqualTo(objectMapper.readTree(
                        "{\"utterances\":[{\"utteranceIndex\":0,\"speaker\":\"CLIENT\",\"startMs\":5680,"
                                + "\"endMs\":6540,\"rawText\":\"안녕하세요.\"}]}"));
    }

    @Test
    void 이미_전사가_있으면_처음_것을_지킨다() {
        service.recordResult(SESSION_ID, RESULT);
        String first = session.getTranscript();

        service.recordResult(SESSION_ID, new TranscriptResultRequest(List.of()));

        assertThat(session.getTranscript()).isEqualTo(first);
    }

    @Test
    void 전사_불가로_판정된_세션에_온_결과는_남기지_않는다() {
        service.recordFailure(SESSION_ID, failure(TranscriptFailureRequest.Code.AUDIO_UNUSABLE));

        service.recordResult(SESSION_ID, RESULT);

        assertThat(session.getTranscript()).isNull();
    }

    @Test
    void 녹음을_쓸_수_없으면_다시_내주지_않게_표시한다() {
        service.recordFailure(SESSION_ID, failure(TranscriptFailureRequest.Code.AUDIO_UNUSABLE));

        assertThat(session.getTranscriptFailedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void 노트북_쪽_문제면_아무것도_바꾸지_않는다() {
        service.recordFailure(SESSION_ID, failure(TranscriptFailureRequest.Code.TRANSCRIBER_UNAVAILABLE));

        assertThat(session.getTranscriptFailedAt()).isNull();
        assertThat(session.getTranscript()).isNull();
    }

    @Test
    void 없는_세션이면_404() {
        when(sessionRepository.findWithLockByIdAndDeletedFalse(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordResult(404, RESULT))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_FOUND);
    }

    /** 개발자(612)와 의뢰인(611)이 녹음을 붙였다. 주어진 파일만 읽기 주소가 만들어진다. */
    private void givenRecordings(Map<Integer, String> readUrls) {
        when(sessionRepository.findNextToTranscribe()).thenReturn(Optional.of(SESSION_ID));
        when(participationRepository.findAllByConsultingSessionIdAndDeletedFalse(SESSION_ID))
                .thenReturn(List.of(recording(DEVELOPER_ID, 612L), recording(CLIENT_ID, 611L)));
        when(fileLookupService.findReadUrls(List.of(612, 611))).thenReturn(readUrls);
    }

    private static ParticipationRecord recording(Integer userId, Long fileId) {
        ParticipationRecord record = ParticipationRecord.consent(
                SESSION_ID, userId, (short) 1, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        record.attachRecording(fileId);
        return record;
    }

    private static TranscriptFailureRequest failure(TranscriptFailureRequest.Code code) {
        return new TranscriptFailureRequest(code, "사유");
    }
}
