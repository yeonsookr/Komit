package com.a501.service.domain.consulting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.entity.ParticipationRecord;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 다음에 전사할 세션을 고르는 조건을 실제 PostgreSQL 18 에서 봅니다. 서브쿼리와 json 컬럼이 걸려 있습니다. */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class ConsultingSessionRepositoryTest {
    private static final LocalDateTime ENDED = LocalDateTime.of(2026, 9, 25, 10, 0);
    private static final int CLIENT_ID = 7;
    private static final int DEVELOPER_ID = 9;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Autowired
    private ConsultingSessionRepository sessionRepository;
    @Autowired
    private ParticipationRecordRepository participationRepository;

    @BeforeEach
    void setUp() {
        participationRepository.deleteAll();
        sessionRepository.deleteAll();
    }

    @Test
    void 두_녹음이_붙은_끝난_세션을_내준다() {
        Integer id = endedWithRecordings(ENDED, 2);

        assertThat(sessionRepository.findNextToTranscribe()).contains(id);
    }

    @Test
    void 가장_먼저_끝난_세션부터_내준다() {
        endedWithRecordings(ENDED.plusMinutes(30), 2);
        Integer earlier = endedWithRecordings(ENDED, 2);

        assertThat(sessionRepository.findNextToTranscribe()).contains(earlier);
    }

    @Test
    void 녹음이_한_건뿐이면_내주지_않는다() {
        endedWithRecordings(ENDED, 1);

        assertThat(sessionRepository.findNextToTranscribe()).isEmpty();
    }

    @Test
    void 끝나지_않은_세션은_내주지_않는다() {
        ConsultingSession session = sessionRepository.save(ConsultingSession.of(1, 50_000, 30));
        record(session.getId(), CLIENT_ID, 611L);
        record(session.getId(), DEVELOPER_ID, 612L);

        assertThat(sessionRepository.findNextToTranscribe()).isEmpty();
    }

    @Test
    void 전사가_있으면_내주지_않는다() {
        Integer id = endedWithRecordings(ENDED, 2);
        ConsultingSession session = sessionRepository.findById(id).orElseThrow();
        session.recordTranscript("{\"utterances\": []}");
        sessionRepository.saveAndFlush(session);

        assertThat(sessionRepository.findNextToTranscribe()).isEmpty();
        // json 컬럼에 저장되고 그대로 읽힌다.
        assertThat(sessionRepository.findById(id).orElseThrow().getTranscript()).contains("utterances");
    }

    @Test
    void 전사_불가로_판정된_세션은_내주지_않는다() {
        Integer id = endedWithRecordings(ENDED, 2);
        ConsultingSession session = sessionRepository.findById(id).orElseThrow();
        session.markTranscriptFailed(ENDED.plusMinutes(1));
        sessionRepository.saveAndFlush(session);

        assertThat(sessionRepository.findNextToTranscribe()).isEmpty();
    }

    @Test
    void 지워진_세션과_지워진_녹음은_세지_않는다() {
        Integer deletedSession = endedWithRecordings(ENDED, 2);
        ConsultingSession session = sessionRepository.findById(deletedSession).orElseThrow();
        ReflectionTestUtils.setField(session, "deleted", true);
        sessionRepository.saveAndFlush(session);

        Integer deletedRecording = endedWithRecordings(ENDED.plusMinutes(5), 2);
        ParticipationRecord record = participationRepository
                .findAllByConsultingSessionIdAndDeletedFalse(deletedRecording).getFirst();
        ReflectionTestUtils.setField(record, "deleted", true);
        participationRepository.saveAndFlush(record);

        assertThat(sessionRepository.findNextToTranscribe()).isEmpty();
    }

    private Integer endedWithRecordings(LocalDateTime endedAt, int recordings) {
        ConsultingSession session = ConsultingSession.of(1, 50_000, 30);
        session.completeConsulting(endedAt);
        Integer id = sessionRepository.save(session).getId();
        record(id, CLIENT_ID, 611L);
        record(id, DEVELOPER_ID, recordings == 2 ? 612L : null);
        return id;
    }

    private void record(Integer sessionId, Integer userId, Long fileId) {
        ParticipationRecord record = ParticipationRecord.consent(sessionId, userId, (short) 1, ENDED);
        if (fileId != null) {
            record.attachRecording(fileId);
        }
        participationRepository.saveAndFlush(record);
    }
}
