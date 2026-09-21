package com.a501.service.domain.consulting.repository;

import com.a501.service.domain.consulting.entity.ParticipationRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipationRecordRepository extends JpaRepository<ParticipationRecord, Integer> {
    List<ParticipationRecord> findAllByConsultingSessionIdAndDeletedFalse(Integer consultingSessionId);
}
