package com.a501.service.domain.consulting.repository;

import com.a501.service.domain.consulting.entity.ConsultingSession;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ConsultingSessionRepository extends JpaRepository<ConsultingSession, Integer> {
    Optional<ConsultingSession> findByIdAndDeletedFalse(Integer id);

    /**
     * 입장을 줄 세우려고 세션 행을 잠그고 가져옵니다.
     *
     * <p>입장은 「동의가 있나 보고 없으면 넣는다」와 「모였나 세어 본다」를 잇달아 합니다. 잠그지
     * 않으면 둘이 겹칠 때 둘 다 「없다」를 보고 둘 다 넣어 참여 기록 유니크 제약에 걸리고, 둘 다
     * 「아직 한 명」을 세어 양쪽이 대기에 머뭅니다. 같은 세션의 입장만 기다리므로 다른 세션은
     * 서로를 막지 않습니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ConsultingSession> findWithLockByIdAndDeletedFalse(Integer id);

    /** 여러 컨설팅 합의에 달린 세션. 마이페이지의 예정 세션 수를 셀 때 씁니다. */
    List<ConsultingSession> findByConsultingAgreementIdInAndDeletedFalse(Collection<Integer> consultingAgreementIds);

    /** 한 컨설팅 합의에 달린 세션. 계약서 조회가 세션 순서대로 보여줄 때 씁니다. */
    List<ConsultingSession> findAllByConsultingAgreementIdAndDeletedFalseOrderByIdAsc(
            Integer consultingAgreementId);
}
