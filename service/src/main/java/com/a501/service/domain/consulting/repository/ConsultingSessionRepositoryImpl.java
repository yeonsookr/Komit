package com.a501.service.domain.consulting.repository;

import com.a501.service.domain.consulting.entity.QConsultingSession;
import com.a501.service.domain.consulting.entity.QParticipationRecord;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/** {@link ConsultingSessionRepositoryCustom} 의 QueryDSL 구현. 녹음 수를 세는 서브쿼리가 있어 여기 둡니다. */
@RequiredArgsConstructor
public class ConsultingSessionRepositoryImpl implements ConsultingSessionRepositoryCustom {
    /** 녹음은 참가자당 한 건이고 세션 하나에 참가자는 둘이다. (R-CON-14) */
    private static final long RECORDINGS_PER_SESSION = 2;

    private static final QConsultingSession SESSION = QConsultingSession.consultingSession;
    private static final QParticipationRecord RECORD = QParticipationRecord.participationRecord;

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Integer> findNextToTranscribe() {
        return Optional.ofNullable(queryFactory
                .select(SESSION.id)
                .from(SESSION)
                .where(
                        SESSION.deleted.isFalse(),
                        SESSION.endedAt.isNotNull(),
                        SESSION.transcript.isNull(),
                        SESSION.transcriptFailedAt.isNull(),
                        JPAExpressions.select(RECORD.count())
                                .from(RECORD)
                                .where(
                                        RECORD.consultingSessionId.eq(SESSION.id),
                                        RECORD.deleted.isFalse(),
                                        RECORD.fileId.isNotNull())
                                .eq(RECORDINGS_PER_SESSION))
                .orderBy(SESSION.endedAt.asc(), SESSION.id.asc())
                .fetchFirst());
    }
}
