package com.a501.service.domain.consulting.repository;

import com.a501.service.domain.consulting.entity.ConsultingSession;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultingSessionRepository extends JpaRepository<ConsultingSession, Integer> {
    Optional<ConsultingSession> findByIdAndDeletedFalse(Integer id);

    /** 여러 컨설팅 합의에 달린 세션. 마이페이지의 예정 세션 수를 셀 때 씁니다. */
    List<ConsultingSession> findByConsultingAgreementIdInAndDeletedFalse(Collection<Integer> consultingAgreementIds);

    /** 한 컨설팅 합의에 달린 세션. 계약서 조회가 세션 순서대로 보여줄 때 씁니다. */
    List<ConsultingSession> findAllByConsultingAgreementIdAndDeletedFalseOrderByIdAsc(
            Integer consultingAgreementId);
}
